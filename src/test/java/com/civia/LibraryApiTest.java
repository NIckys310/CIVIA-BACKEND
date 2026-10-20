package com.civia;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

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

/** Biblioteca: ingeniería civil, artículo del día, estadísticas, glosario, búsquedas y artículos propios. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:civia-library;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "civia.storage.dir=target/test-uploads",
        "civia.security.keys-dir=target/test-keys",
        "civia.ai.anthropic-api-key=",
        "civia.web.dir=target/no-web",
        "civia.mail.mode=log",
        "civia.security.auth-requests-per-minute=10000",
        "civia.mail.public-url=http://civia.test"
})
class LibraryApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean PwnedPasswords pwned;

    @BeforeEach
    void noBreaches() {
        when(pwned.breachCount(anyString())).thenReturn(0L);
    }

    private String token(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Topógrafa\",\"email\":\"" + email + "\",\"password\":\"Biblioteca-Segura-2026\"}"))
                .andExpect(status().isCreated()).andReturn();
        return "Bearer " + json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("access_token").asText();
    }

    @Test
    void civilEngineeringCategoriesAreListed() throws Exception {
        String auth = token("categorias@civia.test");
        mvc.perform(get("/api/v1/library/categories").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem("geotecnia")))
                .andExpect(jsonPath("$[*].id", hasItem("hidraulica")))
                .andExpect(jsonPath("$[*].id", hasItem("vias")));
    }

    @Test
    void featuredAndStats() throws Exception {
        String auth = token("destacado@civia.test");
        mvc.perform(get("/api/v1/library/featured").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slug").isNotEmpty());
        mvc.perform(get("/api/v1/library/stats").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articles", greaterThanOrEqualTo(140)))
                .andExpect(jsonPath("$.terms", greaterThanOrEqualTo(260)));
    }

    @Test
    void glossaryCanBeFilteredByCategory() throws Exception {
        String auth = token("glosario@civia.test");
        mvc.perform(get("/api/v1/library/terms").param("category", "vias").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].category", everyItem(is("vias"))))
                .andExpect(jsonPath("$[*].term", hasItem("Subrasante")));
    }

    @Test
    void recentSearchesAreMineAndUnique() throws Exception {
        String auth = token("recientes@civia.test");
        for (String q : new String[] {"talud", "pavimento", "talud"}) {
            mvc.perform(get("/api/v1/library/search").param("q", q).header("Authorization", auth)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/library/recent-searches").header("Authorization", auth))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("talud"));
        String other = token("ajena@civia.test");
        mvc.perform(get("/api/v1/library/recent-searches").header("Authorization", other))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void onlyTheAuthorEditsOrDeletesAnArticle() throws Exception {
        String author = token("autora@civia.test");
        MvcResult created = mvc.perform(post("/api/v1/library/articles").header("Authorization", author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"geotecnia\",\"title\":\"Suelos de la sabana\",\"body\":\"## Arcillas\\nBlandas y compresibles.\"}"))
                .andExpect(status().isCreated()).andReturn();
        String slug = json.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("slug").asText();

        mvc.perform(get("/api/v1/library/articles/" + slug).header("Authorization", author))
                .andExpect(jsonPath("$.mine").value(true));

        String intruder = token("intruso@civia.test");
        mvc.perform(get("/api/v1/library/articles/" + slug).header("Authorization", intruder))
                .andExpect(jsonPath("$.mine").value(false));
        mvc.perform(put("/api/v1/library/articles/" + slug).header("Authorization", intruder)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Hackeado\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/library/articles/escaleras").header("Authorization", author))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/v1/library/articles/" + slug).header("Authorization", author)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Suelos blandos de la sabana\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Suelos blandos de la sabana"));
        mvc.perform(delete("/api/v1/library/articles/" + slug).header("Authorization", author))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/library/articles/" + slug).header("Authorization", author))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/library/articles").param("category", "geotecnia").header("Authorization", author))
                .andExpect(jsonPath("$[*].slug", not(hasItem(slug))));
    }
}
