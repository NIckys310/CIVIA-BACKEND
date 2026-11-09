package com.civia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.civia.security.PwnedPasswords;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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

/** Calculadoras, modelo 3D paramétrico e informes PDF. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:civia-calc;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "civia.storage.dir=target/test-uploads",
        "civia.security.keys-dir=target/test-keys",
        "civia.ai.anthropic-api-key=",
        "civia.web.dir=target/no-web",
        "civia.mail.mode=log",
        "civia.security.auth-requests-per-minute=10000",
        "civia.mail.public-url=http://civia.test"
})
class CalcModelReportTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean PwnedPasswords pwned;

    @BeforeEach
    void noBreaches() {
        when(pwned.breachCount(anyString())).thenReturn(0L);
    }

    private String token(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ingeniera Calculista\",\"email\":\"" + email + "\",\"password\":\"Calculo-Seguro-2026\"}"))
                .andExpect(status().isCreated()).andReturn();
        return "Bearer " + read(r).get("access_token").asText();
    }

    private JsonNode read(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String project(String auth, String name) throws Exception {
        return read(mvc.perform(post("/api/v1/projects").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}")).andExpect(status().is2xxSuccessful()).andReturn()).get("id").asText();
    }

    private static String pdfText(byte[] bytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void catalogIsCachedWithAnEtag() throws Exception {
        String auth = token("catalogo@civia.test");
        MvcResult first = mvc.perform(get("/api/v1/calc").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=3600")))
                .andReturn();
        String tag = first.getResponse().getHeader("ETag");
        mvc.perform(get("/api/v1/calc").header("Authorization", auth).header("If-None-Match", tag))
                .andExpect(status().isNotModified());
    }

    @Test
    void runsACalculatorAndRejectsBadInput() throws Exception {
        String auth = token("viga@civia.test");
        mvc.perform(post("/api/v1/calc/viga").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputs\":{\"L\":6,\"w\":25,\"P\":0}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values[0].value").value(112.5))
                .andExpect(jsonPath("$.steps").isArray());
        mvc.perform(post("/api/v1/calc/viga").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputs\":{\"L\":500}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/calc/nada").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/calc/viga").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void calculationReportIsAPdfWithTheProject() throws Exception {
        String auth = token("memoria@civia.test");
        String id = project(auth, "Bodega Norte");
        MvcResult r = mvc.perform(post("/api/v1/calc/zapata/report").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputs\":{\"P\":800,\"qadm\":150},\"projectId\":\"" + id + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/pdf")))
                .andReturn();
        String text = pdfText(r.getResponse().getContentAsByteArray());
        assertThat(text).contains("Memoria de cálculo").contains("Bodega Norte").contains("Lado de la zapata");
    }

    @Test
    void modelIsSavedLoadedWithRevisionsAndQuantities() throws Exception {
        String auth = token("modelo@civia.test");
        String id = project(auth, "Edificio Mirador");
        mvc.perform(get("/api/v1/projects/" + id + "/model").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(0)).andExpect(jsonPath("$.model.elements.length()").value(0));

        String model = "{\"version\":1,\"elements\":["
                + "{\"type\":\"column\",\"material\":\"concreto\",\"params\":{\"b\":0.4,\"h\":0.4,\"height\":3}},"
                + "{\"type\":\"beam\",\"material\":\"acero\",\"params\":{\"b\":0.2,\"h\":0.4,\"length\":5}},"
                + "{\"type\":\"wall\",\"material\":\"ladrillo\",\"params\":{\"length\":5,\"height\":2.6,\"thickness\":0.15}},"
                + "{\"type\":\"door\",\"material\":\"madera\",\"params\":{\"width\":0.9,\"height\":2.1}},"
                + "{\"type\":\"window\",\"material\":\"vidrio\",\"params\":{\"width\":1.2,\"height\":1.2}}]}";
        mvc.perform(put("/api/v1/projects/" + id + "/model").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":" + model + ",\"revision\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
        mvc.perform(put("/api/v1/projects/" + id + "/model").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":" + model + ",\"revision\":0}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/projects/" + id + "/model/quantities").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.concreteM3").value(0.48))
                .andExpect(jsonPath("$.totals.steelKg").value(3140.0))
                .andExpect(jsonPath("$.totals.masonryM2").value(13.0))
                .andExpect(jsonPath("$.totals.doors").value(1))
                .andExpect(jsonPath("$.totals.windows").value(1));

        String other = token("ajeno-modelo@civia.test");
        mvc.perform(get("/api/v1/projects/" + id + "/model").header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/projects/" + id + "/model").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":{\"nada\":true}}")).andExpect(status().isBadRequest());
    }

    @Test
    void projectReportIncludesItsSections() throws Exception {
        String auth = token("informe@civia.test");
        String id = project(auth, "Casa Ríos");
        mvc.perform(post("/api/v1/projects/" + id + "/tasks").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Revisar planos estructurales\"}")).andExpect(status().is2xxSuccessful());
        mvc.perform(put("/api/v1/projects/" + id + "/model").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":{\"elements\":[{\"type\":\"slab\",\"params\":{\"width\":5,\"length\":6,\"thickness\":0.15}}]}}"))
                .andExpect(status().isOk());
        MvcResult r = mvc.perform(get("/api/v1/projects/" + id + "/report.pdf").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".pdf")))
                .andReturn();
        String text = pdfText(r.getResponse().getContentAsByteArray());
        assertThat(text).contains("Informe de proyecto").contains("Casa Ríos").contains("Revisar planos estructurales")
                .contains("Cantidades del modelo 3D").contains("4,50");

        String other = token("ajeno-informe@civia.test");
        mvc.perform(get("/api/v1/projects/" + id + "/report.pdf").header("Authorization", other)).andExpect(status().isNotFound());
    }

    @Test
    void libraryAnswersCarryAnEtagAndRevalidate() throws Exception {
        String auth = token("biblioteca-etag@civia.test");
        MvcResult first = mvc.perform(get("/api/v1/library/categories").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-cache"))
                .andReturn();
        String tag = first.getResponse().getHeader("ETag");
        assertThat(tag).isNotBlank();
        mvc.perform(get("/api/v1/library/categories").header("Authorization", auth).header("If-None-Match", tag))
                .andExpect(status().isNotModified());
        // Lo privado sigue sin caché.
        mvc.perform(get("/api/v1/projects").header("Authorization", auth))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }
}
