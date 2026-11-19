package com.civia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.civia.domain.User;
import com.civia.domain.UserToken;
import com.civia.repo.UserRepository;
import com.civia.repo.UserTokenRepository;
import com.civia.security.Hashing;
import com.civia.security.PwnedPasswords;
import com.civia.service.AiQuota;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/** Presupuesto desde el modelo, moderación de la biblioteca y verificación de correo. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:civia-features;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "civia.storage.dir=target/test-uploads",
        "civia.security.keys-dir=target/test-keys",
        "civia.ai.anthropic-api-key=",
        "civia.web.dir=target/no-web",
        "civia.mail.mode=log",
        "civia.security.auth-requests-per-minute=10000",
        "civia.mail.public-url=http://civia.test",
        "civia.library.moderation=true",
        "civia.security.admin-emails=jefa@civia.test"
})
class ProductFeaturesTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired UserTokenRepository tokens;
    @MockitoBean PwnedPasswords pwned;

    @BeforeEach
    void noBreaches() {
        when(pwned.breachCount(anyString())).thenReturn(0L);
    }

    private String token(String email) throws Exception {
        var r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Ingeniera\",\"email\":\"" + email + "\",\"password\":\"Producto-Seguro-2026\"}"))
                .andExpect(status().isCreated()).andReturn();
        return "Bearer " + read(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("access_token").asText();
    }

    private JsonNode read(String body) throws Exception {
        return json.readTree(body);
    }

    private JsonNode call(org.springframework.test.web.servlet.RequestBuilder b) throws Exception {
        return read(mvc.perform(b).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void budgetIsBuiltFromTheModelAndKeepsPricesWhenRecalculated() throws Exception {
        String auth = token("presupuesto@civia.test");
        String id = call(post("/api/v1/projects").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Obra\",\"type\":\"BUILDING\"}")).get("id").asText();
        call(put("/api/v1/projects/" + id + "/model").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("""
                {"model":{"version":1,"elements":[
                  {"type":"slab","material":"concreto","params":{"width":5,"length":4,"thickness":0.2}},
                  {"type":"truss","material":"acero","params":{"span":10,"height":2,"panels":8,"size":0.1}},
                  {"type":"double_door","material":"madera","params":{"width":1.6,"height":2.1}}]}}"""));

        JsonNode budget = call(post("/api/v1/projects/" + id + "/budget/from-model").header("Authorization", auth));
        JsonNode items = budget.at("/chapters/0/items");
        assertThat(items).hasSize(3);
        JsonNode slab = find(items, "Losas");
        assertThat(slab.get("unit").asText()).isEqualTo("m3");
        assertThat(slab.get("quantity").asDouble()).isEqualTo(4.0);
        assertThat(find(items, "Cerchas").get("unit").asText()).isEqualTo("kg");
        assertThat(find(items, "Puertas dobles").get("unit").asText()).isEqualTo("und");
        assertThat(find(items, "Puertas dobles").get("quantity").asDouble()).isEqualTo(1.0);

        call(patch("/api/v1/projects/" + id + "/budget/items/" + slab.get("id").asText()).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"unitPrice\":500000}"));
        JsonNode again = call(post("/api/v1/projects/" + id + "/budget/from-model").header("Authorization", auth));
        assertThat(again.get("chapters")).hasSize(1);
        assertThat(find(again.at("/chapters/0/items"), "Losas").get("unitPrice").asDouble()).isEqualTo(500000.0);
    }

    private static JsonNode find(JsonNode items, String prefix) {
        for (JsonNode i : items) if (i.get("description").asText().startsWith(prefix)) return i;
        throw new AssertionError("No está: " + prefix);
    }

    @Test
    void articlesStayPrivateUntilAnAdminApprovesThem() throws Exception {
        String author = token("autora-mod@civia.test");
        String reader = token("lectora-mod@civia.test");
        String admin = token("jefa@civia.test");
        JsonNode created = call(post("/api/v1/library/articles").header("Authorization", author).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Nota sobre taludes\",\"category\":\"geotecnia\",\"body\":\"Contenido de prueba\"}"));
        String slug = created.get("slug").asText();
        assertThat(created.get("pending").asBoolean()).isTrue();

        mvc.perform(get("/api/v1/library/articles/" + slug).header("Authorization", reader)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/library/articles/" + slug).header("Authorization", author)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/library/articles/" + slug + "/approve").header("Authorization", reader)).andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/library/pending").header("Authorization", admin))
                .andExpect(jsonPath("$.canApprove").value(true)).andExpect(jsonPath("$.items.length()").value(1));
        call(post("/api/v1/library/articles/" + slug + "/approve").header("Authorization", admin));
        mvc.perform(get("/api/v1/library/articles/" + slug).header("Authorization", reader)).andExpect(status().isOk());
    }

    @Test
    void anEmailLinkConfirmsTheAccountOnlyOnce() throws Exception {
        token("verifica@civia.test");
        User user = users.findByEmail("verifica@civia.test").orElseThrow();
        UserToken t = new UserToken();
        t.setUser(user);
        t.setPurpose(UserToken.EMAIL_VERIFY);
        t.setTokenHash(Hashing.sha256("enlace-de-prueba"));
        t.setExpiresAt(Instant.now().plusSeconds(600));
        tokens.save(t);
        String body = "{\"token\":\"enlace-de-prueba\"}";
        mvc.perform(post("/api/v1/auth/email/verify").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        assertThat(users.findByEmail("verifica@civia.test").orElseThrow().getEmailVerifiedAt()).isNotNull();
        mvc.perform(post("/api/v1/auth/email/verify").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }

    @Test
    void unverifiedAccountsCannotSpendAiWhenRequired() {
        UserRepository repo = mock(UserRepository.class);
        User user = new User();
        when(repo.findById(any(UUID.class))).thenReturn(Optional.of(user));
        AiQuota quota = new AiQuota(20, true, repo);
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> quota.acquire(id)).isInstanceOf(ResponseStatusException.class);
        user.setEmailVerifiedAt(Instant.now());
        quota.acquire(id);
    }
}
