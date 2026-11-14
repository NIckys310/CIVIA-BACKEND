package com.civia.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.KeyAgreement;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** El canal sellado: saludo ECDH, ida y vuelta cifrada, manipulación y caducidad. */
class SealFilterTest {

    private final ObjectMapper json = new ObjectMapper();

    /** Controlador de prueba: devuelve en JSON el cuerpo que le llegó (ya descifrado). */
    private static final class Echo extends HttpServlet {
        @Override
        protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
            String body = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            res.setContentType("application/json");
            res.getWriter().write("{\"got\":" + (body.isEmpty() ? "null" : body) + ",\"type\":\"" + req.getContentType() + "\"}");
        }
    }

    private record Client(String id, byte[] key) { }

    private Client handshake(SealFilter filter) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair mine = gen.generateKeyPair();
        String pub = Base64.getEncoder().encodeToString(SealedChannel.encodePoint((ECPublicKey) mine.getPublic()));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/seal");
        req.setContentType("application/json");
        req.setContent(("{\"publicKey\":\"" + pub + "\"}").getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(200);
        JsonNode r = json.readTree(res.getContentAsByteArray());

        byte[] raw = Base64.getDecoder().decode(r.path("publicKey").asText());
        ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)), new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
        var server = KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, ((ECPublicKey) mine.getPublic()).getParams()));
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(mine.getPrivate());
        ka.doPhase(server, true);
        String id = r.path("id").asText();
        byte[] key = SealedChannel.hkdf(ka.generateSecret(), id.getBytes(StandardCharsets.UTF_8), SealedChannel.INFO.getBytes(StandardCharsets.UTF_8), 32);
        return new Client(id, key);
    }

    private MockHttpServletResponse send(SealFilter filter, Client c, String path, byte[] body) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.addHeader(SealFilter.HEADER, c.id());
        req.setContentType("application/json");
        req.setContent(body);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain(new Echo()));
        return res;
    }

    @Test
    void credentialsTravelSealedBothWays() throws Exception {
        SealFilter filter = new SealFilter(new SealedChannel());
        Client c = handshake(filter);
        String secret = "{\"email\":\"ana@estudio.co\",\"password\":\"una frase muy larga\"}";
        byte[] sealed = SealedChannel.seal(c.key(), secret.getBytes(StandardCharsets.UTF_8), "req|POST /api/v1/auth/token", new SecureRandom());
        assertThat(new String(sealed, StandardCharsets.UTF_8)).doesNotContain("ana@estudio.co").doesNotContain("frase");

        MockHttpServletResponse res = send(filter, c, "/api/v1/auth/token", sealed);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getHeader(SealFilter.SEALED)).isEqualTo("1");
        String wire = res.getContentAsString();
        assertThat(wire).doesNotContain("ana@estudio.co");

        JsonNode env = json.readTree(wire);
        byte[] plain = SealedChannel.unseal(c.key(), env.path("iv").asText(), env.path("data").asText(), "res|POST /api/v1/auth/token");
        JsonNode echoed = json.readTree(plain);
        assertThat(echoed.path("got").path("email").asText()).isEqualTo("ana@estudio.co");
        assertThat(echoed.path("type").asText()).isEqualTo("application/json");
    }

    @Test
    void aBodySealedForOneRouteIsRejectedOnAnother() throws Exception {
        SealFilter filter = new SealFilter(new SealedChannel());
        Client c = handshake(filter);
        byte[] sealed = SealedChannel.seal(c.key(), "{}".getBytes(StandardCharsets.UTF_8), "req|POST /api/v1/auth/token", new SecureRandom());
        assertThat(send(filter, c, "/api/v1/auth/register", sealed).getStatus()).isEqualTo(400);
    }

    @Test
    void tamperedCiphertextIsRejected() throws Exception {
        SealFilter filter = new SealFilter(new SealedChannel());
        Client c = handshake(filter);
        JsonNode env = json.readTree(SealedChannel.seal(c.key(), "{\"a\":1}".getBytes(StandardCharsets.UTF_8), "req|POST /api/v1/x", new SecureRandom()));
        byte[] data = Base64.getDecoder().decode(env.path("data").asText());
        data[0] ^= 1;
        String bad = "{\"iv\":\"" + env.path("iv").asText() + "\",\"data\":\"" + Base64.getEncoder().encodeToString(data) + "\"}";
        assertThat(send(filter, c, "/api/v1/x", bad.getBytes(StandardCharsets.UTF_8)).getStatus()).isEqualTo(400);
    }

    @Test
    void unknownOrExpiredChannelAsksForANewHandshake() throws Exception {
        Instant[] now = { Instant.parse("2026-01-01T00:00:00Z") };
        Clock clock = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now[0]; }
        };
        SealFilter filter = new SealFilter(new SealedChannel(clock, 60_000, 100));
        Client c = handshake(filter);
        assertThat(send(filter, new Client("desconocido", c.key()), "/api/v1/x", new byte[0]).getStatus()).isEqualTo(428);
        now[0] = now[0].plusSeconds(61);
        assertThat(send(filter, c, "/api/v1/x", new byte[0]).getStatus()).isEqualTo(428);
    }

    @Test
    void pointsOffTheCurveAreRefused() throws Exception {
        SealFilter filter = new SealFilter(new SealedChannel());
        byte[] bogus = new byte[65];
        bogus[0] = 0x04;
        bogus[64] = 7;
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/seal");
        req.setContent(("{\"publicKey\":\"" + Base64.getEncoder().encodeToString(bogus) + "\"}").getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(400);
    }

    @Test
    void requestsWithoutTheHeaderPassThroughUnchanged() throws Exception {
        SealFilter filter = new SealFilter(new SealedChannel());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/status");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain(new Echo()));
        assertThat(res.getHeader(SealFilter.SEALED)).isNull();
        assertThat(res.getContentAsString()).contains("\"got\":null");
    }
}
