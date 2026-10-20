package com.civia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.civia.security.PwnedPasswords;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** CIVIA Copilot en streaming (SSE): eventos, guardado en el historial y autorización. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:civia-stream;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "civia.storage.dir=target/test-uploads",
        "civia.security.keys-dir=target/test-keys",
        "civia.ai.anthropic-api-key=",
        "civia.web.dir=target/no-web",
        "civia.mail.mode=log",
        "civia.security.auth-requests-per-minute=10000",
        "civia.mail.public-url=http://civia.test"
})
class CopilotStreamTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean PwnedPasswords pwned;

    @BeforeEach
    void noBreaches() {
        when(pwned.breachCount(anyString())).thenReturn(0L);
    }

    private String token(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ingeniera Civil\",\"email\":\"" + email + "\",\"password\":\"Copiloto-Seguro-2026\"}"))
                .andExpect(status().isCreated()).andReturn();
        return "Bearer " + json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("access_token").asText();
    }

    /** Espera a que el flujo SSE termine y devuelve su texto. */
    private String streamed(MvcResult r) throws Exception {
        Instant limit = Instant.now().plus(Duration.ofSeconds(20));
        String body = "";
        while (Instant.now().isBefore(limit)) {
            body = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (body.contains("event:done") || body.contains("event:error")) return body;
            Thread.sleep(50);
        }
        return body;
    }

    @Test
    void streamSendsStartAndDoneAndSavesTheConversation() throws Exception {
        String auth = token("stream@civia.test");
        MvcResult r = mvc.perform(post("/api/v1/ai/chat/stream").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"¿Qué es la licuación de suelos?\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = streamed(r);
        assertThat(body).contains("event:start").contains("event:done").doesNotContain("event:error");
        assertThat(body.indexOf("event:start")).isLessThan(body.indexOf("event:done"));
        assertThat(r.getResponse().getHeader("Cache-Control")).contains("no-cache");

        String done = body.substring(body.indexOf("event:done"));
        JsonNode saved = json.readTree(done.substring(done.indexOf("data:") + 5, done.indexOf('\n', done.indexOf("data:"))).strip());
        assertThat(saved.at("/message/role").asText()).isEqualTo("assistant");
        assertThat(saved.at("/message/content").asText().toLowerCase()).contains("licuaci");

        String conversationId = saved.get("conversationId").asText();
        MvcResult history = mvc.perform(get("/api/v1/ai/conversations/" + conversationId).header("Authorization", auth))
                .andExpect(status().isOk()).andReturn();
        JsonNode messages = json.readTree(history.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("role").asText()).isEqualTo("user");
    }

    @Test
    void streamRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/ai/chat/stream").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hola\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void streamRejectsEmptyMessagesBeforeStreaming() throws Exception {
        String auth = token("vacio@civia.test");
        mvc.perform(post("/api/v1/ai/chat/stream").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void streamCannotContinueSomeoneElsesConversation() throws Exception {
        String owner = token("duena@civia.test");
        MvcResult r = mvc.perform(post("/api/v1/ai/chat").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"¿Qué es una zapata?\"}"))
                .andExpect(status().isOk()).andReturn();
        String conversationId = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("conversationId").asText();

        String intruder = token("intrusa@civia.test");
        mvc.perform(post("/api/v1/ai/chat/stream").header("Authorization", intruder).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + conversationId + "\",\"message\":\"muéstrame todo\"}"))
                .andExpect(status().isNotFound());
    }
}
