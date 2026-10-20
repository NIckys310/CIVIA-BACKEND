package com.civia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.civia.domain.User;
import com.civia.domain.UserToken;
import com.civia.repo.UserRepository;
import com.civia.repo.UserTokenRepository;
import com.civia.security.Hashing;
import com.civia.security.PwnedPasswords;
import com.civia.service.MailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:civia;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "civia.storage.dir=target/test-uploads",
        "civia.security.keys-dir=target/test-keys",
        "civia.ai.anthropic-api-key=",
        "civia.web.dir=target/no-web",
        "civia.mail.mode=log",
        // Todas las pruebas comparten la IP 127.0.0.1; el límite real (20/min) se probaría aparte.
        "civia.security.auth-requests-per-minute=10000",
        "civia.mail.public-url=http://civia.test"
})
class ApiFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired UserTokenRepository userTokens;
    /** Sin red en las pruebas: Have I Been Pwned se simula. */
    @MockitoBean PwnedPasswords pwned;
    @MockitoSpyBean MailService mail;

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String register(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ana Arquitecta\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andReturn();
        return body(r).get("access_token").asText();
    }

    @Test
    void passwordsAreHashedWithArgon2AndHaveNoMaxLength() throws Exception {
        String longPassword = "Frase-muy-extensa-" + "x1".repeat(600); // 1216 caracteres
        register("larga@civia.test", longPassword);
        User u = users.findByEmail("larga@civia.test").orElseThrow();
        assertThat(u.getPasswordHash()).startsWith("$argon2id$").doesNotContain(longPassword);

        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grant_type\":\"password\",\"email\":\"LARGA@civia.test\",\"password\":\"" + longPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    @Test
    void weakPasswordsAndBadLoginsAreRejectedAndAccountLocks() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"X\",\"email\":\"debil@civia.test\",\"password\":\"12345678\"}"))
                .andExpect(status().isBadRequest());
        register("bloqueo@civia.test", "Segura-2026!");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"bloqueo@civia.test\",\"password\":\"mala-" + i + "\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bloqueo@civia.test\",\"password\":\"Segura-2026!\"}"))
                .andExpect(status().isLocked());
    }

    private static final Pattern RESET_LINK = Pattern.compile("http://civia\\.test/#/reset\\?token=([A-Za-z0-9_-]{43})");

    /** Espera a que se envíen {@code expected} correos de recuperación a {@code to} y devuelve el token del último. */
    private String awaitResetToken(String to, int expected) throws InterruptedException {
        List<MailService.Mail> resets = List.of();
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            ArgumentCaptor<MailService.Mail> sent = ArgumentCaptor.forClass(MailService.Mail.class);
            verify(mail, timeout(5000).atLeastOnce()).send(sent.capture());
            resets = sent.getAllValues().stream()
                    .filter(m -> m.to().equals(to) && m.subject().startsWith("Restablece")).toList();
            if (resets.size() >= expected) break;
            Thread.sleep(50);
        }
        assertThat(resets).hasSize(expected);
        Matcher m = RESET_LINK.matcher(resets.get(expected - 1).text());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private ResultActions forgot(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email))));
    }

    private ResultActions reset(String token, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token, "password", password))));
    }

    @Test
    void forgotPasswordAnswersTheSameAndResetIsSingleUseAndClosesSessions() throws Exception {
        MvcResult reg = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Olvido\",\"email\":\"olvido@civia.test\",\"password\":\"Antigua-Clave-2026\"}"))
                .andExpect(status().isCreated()).andReturn();
        Cookie session = reg.getResponse().getCookie("civia_rt");

        // Misma respuesta exista o no la cuenta.
        String unknown = forgot("nadie@civia.test").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String known = forgot(" OLVIDO@civia.test ").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(known).isEqualTo(unknown);

        String token = awaitResetToken("olvido@civia.test", 1);
        UserToken stored = userTokens.findByTokenHashAndPurpose(Hashing.sha256(token), UserToken.PASSWORD_RESET).orElseThrow();
        assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(token);
        assertThat(stored.getExpiresAt()).isBetween(Instant.now().plusSeconds(29 * 60), Instant.now().plusSeconds(31 * 60));

        // Una contraseña débil no consume el enlace.
        reset(token, "12345678").andExpect(status().isBadRequest());
        reset(token, "Nueva-Clave-Segura-2026").andExpect(status().isOk()).andExpect(jsonPath("$.reset").value(true));
        // Un solo uso.
        reset(token, "Otra-Clave-Segura-2026").andExpect(status().isBadRequest());
        reset("token-inventado", "Otra-Clave-Segura-2026").andExpect(status().isBadRequest());

        // Se cerraron todas las sesiones y solo sirve la nueva contraseña.
        mvc.perform(post("/api/v1/auth/token").cookie(session).contentType(MediaType.APPLICATION_JSON).content("{\"grant_type\":\"refresh_token\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"olvido@civia.test\",\"password\":\"Antigua-Clave-2026\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"olvido@civia.test\",\"password\":\"Nueva-Clave-Segura-2026\"}"))
                .andExpect(status().isOk());

        // A la cuenta inexistente nunca se le envió nada.
        ArgumentCaptor<MailService.Mail> all = ArgumentCaptor.forClass(MailService.Mail.class);
        verify(mail, atLeastOnce()).send(all.capture());
        assertThat(all.getAllValues()).noneMatch(m -> m.to().equals("nadie@civia.test"));
    }

    @Test
    void resetLinksExpireAndANewRequestInvalidatesThePreviousOne() throws Exception {
        register("vence@civia.test", "Caduca-Pronto-2026");
        forgot("vence@civia.test").andExpect(status().isAccepted());
        String first = awaitResetToken("vence@civia.test", 1);
        forgot("vence@civia.test").andExpect(status().isAccepted());
        String second = awaitResetToken("vence@civia.test", 2);
        reset(first, "Otra-Clave-Segura-2026").andExpect(status().isBadRequest());

        UserToken t = userTokens.findByTokenHashAndPurpose(Hashing.sha256(second), UserToken.PASSWORD_RESET).orElseThrow();
        t.setExpiresAt(Instant.now().minusSeconds(1));
        userTokens.save(t);
        reset(second, "Otra-Clave-Segura-2026").andExpect(status().isBadRequest());
    }

    @Test
    void breachedPasswordsAreRejected() throws Exception {
        when(pwned.breachCount(anyString())).thenReturn(0L);
        when(pwned.breachCount("Filtrada-Conocida-2026")).thenReturn(1234L);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Pwned\",\"email\":\"pwned@civia.test\",\"password\":\"Filtrada-Conocida-2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Have I Been Pwned")));
        mvc.perform(post("/api/v1/auth/password/check").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Filtrada-Conocida-2026\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false));
        register("pwned@civia.test", "Original-Sin-Filtrar-2026");
    }

    @Test
    void protectedEndpointsNeedToken() throws Exception {
        mvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/user").header("Authorization", "Bearer invalido")).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenRotatesAndReuseRevokesFamily() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Rota\",\"email\":\"rota@civia.test\",\"password\":\"Giratoria-2026\"}"))
                .andReturn();
        Cookie first = r.getResponse().getCookie("civia_rt");
        MvcResult r2 = mvc.perform(post("/api/v1/auth/token").cookie(first).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grant_type\":\"refresh_token\"}"))
                .andExpect(status().isOk()).andReturn();
        Cookie second = r2.getResponse().getCookie("civia_rt");
        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        // Reutilizar el primero (robado) invalida también el segundo.
        mvc.perform(post("/api/v1/auth/token").cookie(first).contentType(MediaType.APPLICATION_JSON).content("{\"grant_type\":\"refresh_token\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/token").cookie(second).contentType(MediaType.APPLICATION_JSON).content("{\"grant_type\":\"refresh_token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fullProjectFlowWithUploadsAiAndLibrary() throws Exception {
        String token = register("flujo@civia.test", "Proyecto-Casa-2026");
        String auth = "Bearer " + token;

        mvc.perform(get("/api/v1/auth/user").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("flujo@civia.test"));

        MvcResult created = mvc.perform(post("/api/v1/projects").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Casa Loma\",\"type\":\"Vivienda unifamiliar\",\"floors\":2,\"builtArea\":180}"))
                .andExpect(status().isCreated()).andReturn();
        String projectId = body(created).get("id").asText();

        // Un ejecutable disfrazado de PDF se rechaza.
        mvc.perform(multipart("/api/v1/projects/" + projectId + "/documents")
                        .file(new MockMultipartFile("file", "plano.pdf", "application/pdf", "MZ\u0090fake".getBytes(StandardCharsets.ISO_8859_1)))
                        .header("Authorization", auth))
                .andExpect(status().isUnsupportedMediaType());

        String dxf = "0\nSECTION\n2\nENTITIES\n0\nTEXT\n8\nTEXTOS\n1\nPLANTA PRIMER PISO ESCALA 1:100 COCINA SALA ALCOBA\n0\nENDSEC\n0\nEOF\n";
        MvcResult up = mvc.perform(multipart("/api/v1/projects/" + projectId + "/documents")
                        .file(new MockMultipartFile("file", "planta.dxf", "application/dxf", dxf.getBytes(StandardCharsets.UTF_8)))
                        .param("kind", "PLAN").header("Authorization", auth))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("PLAN")).andReturn();
        String docId = body(up).get("id").asText();

        mvc.perform(post("/api/v1/ai/documents/" + docId + "/analyze").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engine").value("CIVIA local"))
                .andExpect(jsonPath("$.result.checks").isArray());

        mvc.perform(get("/api/v1/documents/" + docId + "/file").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        mvc.perform(get("/api/v1/ai/projects/" + projectId + "/recommendations").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].priority").exists());

        mvc.perform(post("/api/v1/ai/chat").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\",\"message\":\"¿Qué le falta a mi proyecto?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.content").value(org.hamcrest.Matchers.containsString("hace falta")));

        mvc.perform(post("/api/v1/library/ask").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"¿Cómo se calcula una escalera?\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sources[0].slug").value("escaleras"));

        mvc.perform(get("/api/v1/library/categories").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(19));

        mvc.perform(post("/api/v1/projects/" + projectId + "/budget/template").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.chapters.length()").value(14));

        // Otro usuario no ve el proyecto (404, no 403).
        String other = "Bearer " + register("otro@civia.test", "Segundo-Miembro-2026");
        mvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documents/" + docId + "/file").header("Authorization", other)).andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/dashboard").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projects").value(1)).andExpect(jsonPath("$.plans").value(1));
    }
}
