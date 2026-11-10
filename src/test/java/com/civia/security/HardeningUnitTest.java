package com.civia.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Protecciones que no dependen del contexto de Spring. */
class HardeningUnitTest {

    private static MockHttpServletResponse hit(RateLimitFilter filter, String claimedIp, String edgeIp) throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/token");
        req.setRemoteAddr(claimedIp);
        if (edgeIp != null) req.addHeader("CF-Connecting-IP", edgeIp);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void rotatingTheClaimedIpDoesNotEscapeTheEdgeLimit() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, 900);
        int blocked = 0;
        for (int i = 0; i < 40; i++) {
            if (hit(filter, "10.0.0." + i, "203.0.113.9").getStatus() == 429) blocked++;
        }
        assertThat(blocked).isEqualTo(40 - 2 * RateLimitFilter.EDGE_MULTIPLIER);
    }

    @Test
    void clientsBehindTheSameProxyKeepTheirOwnLimit() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, 900);
        for (int i = 0; i < 2; i++) assertThat(hit(filter, "198.51.100.1", "203.0.113.9").getStatus()).isEqualTo(200);
        assertThat(hit(filter, "198.51.100.1", "203.0.113.9").getStatus()).isEqualTo(429);
        assertThat(hit(filter, "198.51.100.2", "203.0.113.9").getStatus()).isEqualTo(200);
    }

    @Test
    void oversizedJsonIsRejectedBeforeReadingIt() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/token");
        req.setContentType("application/json");
        req.setContent(new byte[2048]);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new BodyLimitFilter(1024).doFilter(req, res, chain);
        assertThat(res.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void totpReportsTheStepItMatched() {
        String secret = Totp.newSecret();
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        long step = now.getEpochSecond() / 30;
        assertThat(Totp.matchedStep(secret, Totp.code(secret, step - 1), now)).hasValue(step - 1);
        assertThat(Totp.matchedStep(secret, Totp.code(secret, step), now)).hasValue(step);
        assertThat(Totp.matchedStep(secret, Totp.code(secret, step + 5), now)).isEmpty();
    }
}
