package com.civia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.civia.domain.User;
import com.civia.repo.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:civia;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "civia.storage.dir=target/test-uploads",
        "civia.security.keys-dir=target/test-keys",
        "civia.ai.anthropic-api-key=",
        "civia.web.dir=target/no-web"
})
class ApiFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;

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
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(13));

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
