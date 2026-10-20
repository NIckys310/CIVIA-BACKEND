package com.civia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

/** Gestión del historial del Copilot: buscar, renombrar, fijar, valorar, exportar, regenerar y borrar. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:civia-conversations;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "civia.storage.dir=target/test-uploads",
        "civia.security.keys-dir=target/test-keys",
        "civia.ai.anthropic-api-key=",
        "civia.web.dir=target/no-web",
        "civia.mail.mode=log",
        "civia.security.auth-requests-per-minute=10000",
        "civia.mail.public-url=http://civia.test"
})
class CopilotConversationsTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean PwnedPasswords pwned;

    @BeforeEach
    void noBreaches() {
        when(pwned.breachCount(anyString())).thenReturn(0L);
    }

    private String token(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ingeniero Civil\",\"email\":\"" + email + "\",\"password\":\"Historial-Seguro-2026\"}"))
                .andExpect(status().isCreated()).andReturn();
        return "Bearer " + read(r).get("access_token").asText();
    }

    private JsonNode read(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode ask(String auth, String message) throws Exception {
        return read(mvc.perform(post("/api/v1/ai/chat").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + message + "\"}")).andExpect(status().isOk()).andReturn());
    }

    @Test
    void searchFindsConversationsByTitleAndContent() throws Exception {
        String auth = token("busca@civia.test");
        ask(auth, "¿Qué es una zapata?");
        ask(auth, "Háblame del golpe de ariete");

        mvc.perform(get("/api/v1/ai/conversations").param("q", "ariete").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value(org.hamcrest.Matchers.containsString("ariete")));
        mvc.perform(get("/api/v1/ai/conversations").param("q", "100%_raro").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void renameAndPinMovePinnedFirst() throws Exception {
        String auth = token("fija@civia.test");
        String first = ask(auth, "¿Qué es una losa aligerada?").get("conversationId").asText();
        ask(auth, "¿Qué es el CBR?");

        mvc.perform(patch("/api/v1/ai/conversations/" + first).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  Losas   de entrepiso \",\"pinned\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Losas de entrepiso"))
                .andExpect(jsonPath("$.pinned").value(true));
        mvc.perform(get("/api/v1/ai/conversations").header("Authorization", auth))
                .andExpect(jsonPath("$[0].id").value(first));
        mvc.perform(patch("/api/v1/ai/conversations/" + first).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void feedbackOnlyOnOwnAssistantMessages() throws Exception {
        String auth = token("valora@civia.test");
        JsonNode reply = ask(auth, "¿Qué es un pilote?");
        String messageId = reply.at("/message/id").asText();

        mvc.perform(post("/api/v1/ai/messages/" + messageId + "/feedback").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"up\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.feedback").value(1));
        mvc.perform(post("/api/v1/ai/messages/" + messageId + "/feedback").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"meh\"}"))
                .andExpect(status().isBadRequest());

        String other = token("otra@civia.test");
        mvc.perform(post("/api/v1/ai/messages/" + messageId + "/feedback").header("Authorization", other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"down\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportReturnsMarkdownWithBothSides() throws Exception {
        String auth = token("exporta@civia.test");
        String id = ask(auth, "¿Qué es la deriva de piso?").get("conversationId").asText();
        MvcResult r = mvc.perform(get("/api/v1/ai/conversations/" + id + "/export").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".md")))
                .andReturn();
        String md = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(md).startsWith("# ").contains("## Tú").contains("## CIVIA Copilot").contains("deriva");
    }

    @Test
    void regenerateReplacesTheLastAnswer() throws Exception {
        String auth = token("regenera@civia.test");
        String id = ask(auth, "¿Qué es la socavación?").get("conversationId").asText();

        MvcResult r = mvc.perform(post("/api/v1/ai/conversations/" + id + "/regenerate").header("Authorization", auth))
                .andExpect(request().asyncStarted()).andReturn();
        Instant limit = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(limit) && !r.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("event:done")) {
            Thread.sleep(50);
        }
        assertThat(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("event:done");
        mvc.perform(get("/api/v1/ai/conversations/" + id).header("Authorization", auth))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].role").value("assistant"));
    }

    @Test
    void deleteAllClearsOnlyMyHistory() throws Exception {
        String mine = token("borra@civia.test");
        String theirs = token("conserva@civia.test");
        ask(mine, "¿Qué es un muro de contención?");
        ask(mine, "¿Qué es el método racional?");
        ask(theirs, "¿Qué es un estribo?");

        mvc.perform(delete("/api/v1/ai/conversations").header("Authorization", mine))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(2));
        mvc.perform(get("/api/v1/ai/conversations").header("Authorization", mine)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/ai/conversations").header("Authorization", theirs)).andExpect(jsonPath("$.length()").value(1));
    }
}
