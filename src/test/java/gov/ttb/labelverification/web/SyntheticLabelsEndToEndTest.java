package gov.ttb.labelverification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.ttb.labelverification.ai.ocr.TesseractOcrEngine;
import gov.ttb.labelverification.repository.UserRepository;
import gov.ttb.labelverification.security.AppUserPrincipal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * End-to-end with the real local pipeline (Tesseract) over the synthetic labels in
 * test-labels/. Skipped automatically when Tesseract is not installed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SyntheticLabelsEndToEndTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    UserRepository users;
    @Autowired
    TesseractOcrEngine tesseract;

    AppUserPrincipal applicant;
    AppUserPrincipal specialist;

    @BeforeEach
    void setUp() {
        assumeThat(tesseract.isAvailable()).as("Tesseract installed").isTrue();
        applicant = users.findByEmailIgnoreCase("applicant@example.com").map(AppUserPrincipal::from).orElseThrow();
        specialist = users.findByEmailIgnoreCase("specialist@example.gov").map(AppUserPrincipal::from).orElseThrow();
    }

    private static MockMultipartFile image(String label) throws Exception {
        return new MockMultipartFile("images", "front.png", "image/png",
                Files.readAllBytes(Path.of("test-labels", label, "front.png")));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "aldercrest-bourbon, APPROVED",
            "quillmoor-chardonnay, APPROVED",
            "tidewater-lager, APPROVED",
            "northvale-vodka-flawed, REJECTED"})
    void submittingDeclaredApplicationDataGivesExpectedProposal(String label, String expected) throws Exception {
        JsonNode app = json.readTree(Path.of("test-labels", label, "application.json").toFile());
        MockMultipartHttpServletRequestBuilder req = multipart("/api/v1/labels").file(image(label));
        for (Iterator<Map.Entry<String, JsonNode>> it = app.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            req.param(e.getKey(), e.getValue().asText());
        }
        JsonNode result = json.readTree(mvc.perform(req.with(user(applicant)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(result.get("aiProposedStatus").asText()).isEqualTo(expected);

        if (expected.equals("REJECTED")) {
            JsonNode detail = json.readTree(mvc.perform(get("/api/v1/labels/{id}", result.get("labelId").asText())
                    .with(user(specialist))).andReturn().getResponse().getContentAsString());
            assertThat(detail.get("fields").toString())
                    .contains("Alcohol content mismatch: expected 42%, found 40%")
                    .contains("not in capital letters");
        }
    }

    @Test
    void prefillReadsEveryFieldOfTheBourbonLabel() throws Exception {
        JsonNode p = json.readTree(mvc.perform(multipart("/submit/extract").file(image("aldercrest-bourbon"))
                        .with(user(applicant)).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(p.get("beverageType").asText()).isEqualTo("DISTILLED_SPIRITS");
        assertThat(p.get("containerSizeMl").asInt()).isEqualTo(750);
        JsonNode f = p.get("fields");
        assertThat(f.get("brandName").asText()).isEqualTo("ALDERCREST");
        assertThat(f.get("fancifulName").asText()).isEqualTo("Small Batch");
        assertThat(f.get("classType").asText()).isEqualTo("Kentucky Straight Bourbon Whiskey");
        assertThat(f.get("alcoholContent").asText()).isEqualTo("45% Alc./Vol. (90 Proof)");
        assertThat(f.get("netContents").asText()).isEqualTo("750 mL");
        assertThat(f.get("qualifyingPhrase").asText()).isEqualTo("Distilled and Bottled by");
        assertThat(f.get("nameAndAddress").asText()).isEqualTo("Aldercrest Distilling Co., Bardstown, Kentucky");
        assertThat(f.get("ageStatement").asText()).isEqualTo("Aged 6 Years");
        assertThat(f.has("healthWarning")).as("health warning is never pre-filled").isFalse();
    }

    @ParameterizedTest(name = "prefill {0}")
    @CsvSource({
            "tidewater-lager, MALT_BEVERAGE, 355, TIDEWATER ROW, Lager",
            "quillmoor-chardonnay, WINE, 750, QUILLMOOR CELLARS, Chardonnay",
            "northvale-vodka-flawed, DISTILLED_SPIRITS, 750, NORTHVALE, Vodka"})
    void prefillOtherLabels(String label, String type, int size, String brand, String cls) throws Exception {
        JsonNode p = json.readTree(mvc.perform(multipart("/api/v1/labels/extract").file(image(label))
                .with(user(applicant))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(p.get("beverageType").asText()).isEqualTo(type);
        assertThat(p.get("containerSizeMl").asInt()).isEqualTo(size);
        assertThat(p.get("fields").get("brandName").asText()).isEqualTo(brand);
        assertThat(p.get("fields").get("classType").asText()).isEqualTo(cls);
    }

    @Test
    void prefillThenSubmitOfFlawedLabelIsStillRejected() throws Exception {
        // Even if the applicant accepts every pre-filled value (so ABV "40%" matches the label),
        // the health warning is checked against the statutory text and the title-case prefix fails.
        JsonNode p = json.readTree(mvc.perform(multipart("/api/v1/labels/extract").file(image("northvale-vodka-flawed"))
                .with(user(applicant))).andReturn().getResponse().getContentAsString());
        MockMultipartHttpServletRequestBuilder req = multipart("/api/v1/labels").file(image("northvale-vodka-flawed"));
        req.param("beverageType", p.get("beverageType").asText());
        req.param("containerSizeMl", p.get("containerSizeMl").asText());
        p.get("fields").fields().forEachRemaining(e -> req.param(e.getKey(), e.getValue().asText()));
        JsonNode result = json.readTree(mvc.perform(req.with(user(applicant)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(result.get("aiProposedStatus").asText()).isEqualTo("REJECTED");
    }

    @Test
    void prefillSecurityAndValidation() throws Exception {
        mvc.perform(multipart("/submit/extract").file(image("tidewater-lager")).with(user(applicant)))
                .andExpect(status().isForbidden()); // no CSRF token
        mvc.perform(multipart("/api/v1/labels/extract").file(image("tidewater-lager")).with(user(specialist)))
                .andExpect(status().isForbidden()); // applicants only
        mvc.perform(multipart("/api/v1/labels/extract")
                        .file(new MockMultipartFile("images", "x.png", "image/png", "<svg/>".getBytes()))
                        .with(user(applicant)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(multipart("/submit/extract")
                        .file(new MockMultipartFile("images", "x.png", "image/png", "<svg/>".getBytes()))
                        .with(user(applicant)).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }
}
