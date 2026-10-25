package com.civia;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.civia.security.PwnedPasswords;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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

/** Caché del servidor: la biblioteca y la volumetría se responden desde la caché y se informan sus aciertos. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:civia-cache;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "civia.storage.dir=target/test-uploads",
        "civia.security.keys-dir=target/test-keys",
        "civia.ai.anthropic-api-key=",
        "civia.web.dir=target/no-web",
        "civia.mail.mode=log",
        "civia.security.auth-requests-per-minute=10000",
        "civia.mail.public-url=http://civia.test"
})
class ServerCacheTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean PwnedPasswords pwned;

    @BeforeEach
    void noBreaches() {
        when(pwned.breachCount(anyString())).thenReturn(0L);
    }

    private String token(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Arquitecta Caché\",\"email\":\"" + email + "\",\"password\":\"Cache-Segura-2026\"}"))
                .andExpect(status().isCreated()).andReturn();
        return "Bearer " + json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("access_token").asText();
    }

    @Test
    void libraryAndMassingAreCachedAndReported() throws Exception {
        String auth = token("cache@civia.test");
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/v1/library/categories").header("Authorization", auth)).andExpect(status().isOk());
        }
        String massing = "{\"lotWidth\":20,\"lotDepth\":30,\"occupancyIndex\":0.6,\"constructionIndex\":4,"
                + "\"frontSetback\":3,\"rearSetback\":3,\"sideSetback\":0,\"floorHeight\":2.9,\"maxFloors\":0,\"floors\":40}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/ai/massing").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(massing))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.floors", is(40)))
                    .andExpect(jsonPath("$.exceedsNorm", is(true)));
        }
        mvc.perform(get("/api/v1/system/cache").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(true)))
                .andExpect(jsonPath("$.caches[*].name", hasItem("library")))
                .andExpect(jsonPath("$.caches[*].name", hasItem("massing")))
                .andExpect(jsonPath("$.hits", greaterThanOrEqualTo(3)));
    }

    @Test
    void cacheReportNeedsSession() throws Exception {
        mvc.perform(get("/api/v1/system/cache")).andExpect(status().isUnauthorized());
    }
}
