package com.civia.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** La verificación en dos pasos resiste a quien ya conoce la contraseña. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:civia-mfa;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "civia.storage.dir=target/test-uploads",
        "civia.security.keys-dir=target/test-keys",
        "civia.ai.anthropic-api-key=",
        "civia.web.dir=target/no-web",
        "civia.mail.mode=log",
        "civia.security.auth-requests-per-minute=10000",
        "civia.mail.public-url=http://civia.test"
})
class MfaHardeningTest {

    private static final String PASSWORD = "Doble-Factor-Seguro-2026";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean PwnedPasswords pwned;

    @BeforeEach
    void noBreaches() {
        when(pwned.breachCount(anyString())).thenReturn(0L);
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String currentCode(String secret) {
        return Totp.code(secret, Instant.now().getEpochSecond() / 30);
    }

    /** Crea una cuenta con verificación en dos pasos y devuelve su secreto. */
    private String enroll(String email) throws Exception {
        String auth = "Bearer " + body(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Prueba MFA\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())).get("access_token").asText();
        String secret = body(mvc.perform(post("/api/v1/auth/mfa/setup").header("Authorization", auth))).get("secret").asText();
        mvc.perform(post("/api/v1/auth/mfa/enable").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + currentCode(secret) + "\"}")).andExpect(status().isOk());
        return secret;
    }

    private ResultActions login(String email, String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"grant_type\":\"password\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"mfa_code\":\"" + code + "\"}"));
    }

    @Test
    void repeatedWrongCodesLockTheAccount() throws Exception {
        String secret = enroll("bloqueo-mfa@civia.test");
        for (int i = 0; i < 5; i++) login("bloqueo-mfa@civia.test", "000000").andExpect(status().isUnauthorized());
        login("bloqueo-mfa@civia.test", currentCode(secret)).andExpect(status().isLocked());
    }

    @Test
    void changingThePasswordIsSaved() throws Exception {
        String next = "Otra-Clave-Distinta-2026";
        String auth = "Bearer " + body(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Cambio Clave\",\"email\":\"cambio@civia.test\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())).get("access_token").asText();
        mvc.perform(post("/api/v1/auth/password").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + next + "\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"grant_type\":\"password\",\"email\":\"cambio@civia.test\",\"password\":\"" + next + "\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"grant_type\":\"password\",\"email\":\"cambio@civia.test\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void aCodeCanOnlyBeUsedOnce() throws Exception {
        String secret = enroll("una-vez@civia.test");
        String code = currentCode(secret);
        login("una-vez@civia.test", code).andExpect(status().isOk());
        login("una-vez@civia.test", code).andExpect(status().isUnauthorized());
    }
}
