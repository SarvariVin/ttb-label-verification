package gov.ttb.labelverification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.ttb.labelverification.ai.ExtractedField;
import gov.ttb.labelverification.ai.ExtractionResult;
import gov.ttb.labelverification.ai.PipelineMetrics;
import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.regulatory.FieldName;
import gov.ttb.labelverification.domain.Applicant;
import gov.ttb.labelverification.domain.User;
import gov.ttb.labelverification.domain.UserRole;
import gov.ttb.labelverification.repository.ApplicantRepository;
import gov.ttb.labelverification.repository.UserRepository;
import gov.ttb.labelverification.security.AppUserPrincipal;
import gov.ttb.labelverification.service.ExtractionService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end workflow against H2 with a stubbed AI pipeline: submit → queue →
 * batch approve, plus role and data-level authorization and page rendering.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LabelWorkflowIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    UserRepository users;
    @Autowired
    ApplicantRepository applicants;
    @Autowired
    PasswordEncoder encoder;

    @MockitoBean
    ExtractionService extraction;

    /** Matches app.seed.password in application-test.yml. */
    static final String SEED_PASSWORD = "test-only-seed-password";

    AppUserPrincipal specialist;
    AppUserPrincipal applicant;
    AppUserPrincipal otherApplicant;

    @BeforeEach
    @Transactional
    void setUp() {
        specialist = principal("specialist@example.gov");
        applicant = principal("applicant@example.com");
        if (users.findByEmailIgnoreCase("other@example.com").isEmpty()) {
            Applicant other = applicants.save(new Applicant("Other Brewing Co.", "other@example.com", "Other", null));
            users.save(new User("Other", "other@example.com", encoder.encode("unused"), UserRole.APPLICANT, other));
        }
        otherApplicant = principal("other@example.com");
        // Default stub: the label says exactly what the application says.
        when(extraction.extract(anyList(), any(), anyMap())).thenAnswer(inv -> echo(inv.getArgument(2)));
    }

    private AppUserPrincipal principal(String email) {
        return users.findByEmailIgnoreCase(email).map(AppUserPrincipal::from).orElseThrow();
    }

    private static ExtractionResult echo(Map<FieldName, String> expected) {
        List<ExtractedField> fields = new ArrayList<>();
        expected.forEach((k, v) -> fields.add(new ExtractedField(k, v, 99, "stub", null, 0)));
        return new ExtractionResult(fields, List.of(), BeverageType.DISTILLED_SPIRITS, 5, "stub",
                Map.of(), new PipelineMetrics(1, 1, 0, 5, 10, 1, 0, 0, 0));
    }

    private static MockMultipartFile png(String name) throws Exception {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return new MockMultipartFile("images", name, "image/png", out.toByteArray());
    }

    private String submitBourbon(int sizeMl) throws Exception {
        String body = mvc.perform(multipart("/api/v1/labels").file(png("front.png"))
                        .param("beverageType", "DISTILLED_SPIRITS")
                        .param("containerSizeMl", String.valueOf(sizeMl))
                        .param("brandName", "Sample Reserve")
                        .param("classType", "Kentucky Straight Bourbon Whiskey")
                        .param("alcoholContent", "45% Alc./Vol.")
                        .param("netContents", "750 mL")
                        .with(user(applicant)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("labelId").asText();
    }

    @Test
    void submitThenBatchApprove() throws Exception {
        String id = submitBourbon(750);

        mvc.perform(get("/api/v1/labels/{id}", id).with(user(specialist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.aiProposedStatus").value("APPROVED"));

        String ready = mvc.perform(get("/api/v1/labels").param("queue", "ready").with(user(specialist)))
                .andReturn().getResponse().getContentAsString();
        assertThat(ready).contains(id);

        mvc.perform(post("/api/v1/labels/batch-approve").with(user(specialist))
                        .contentType("application/json").content("{\"labelIds\":[\"" + id + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvedCount").value(1));

        mvc.perform(get("/api/v1/labels/{id}", id).with(user(applicant)))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                // applicants do not see AI scores
                .andExpect(jsonPath("$.overallConfidence").doesNotExist());
    }

    @Test
    void illegalContainerSizeIsProposedForRejection() throws Exception {
        String id = submitBourbon(740);
        mvc.perform(get("/api/v1/labels/{id}", id).with(user(specialist)))
                .andExpect(jsonPath("$.aiProposedStatus").value("REJECTED"));
    }

    @Test
    void specialistFieldReviewDerivesStatus() throws Exception {
        String id = submitBourbon(750);
        JsonNode detail = json.readTree(mvc.perform(get("/api/v1/labels/{id}", id).with(user(specialist)))
                .andReturn().getResponse().getContentAsString());
        String alcoholItem = null;
        for (JsonNode f : detail.get("fields")) {
            if (f.get("fieldName").asText().equals("ALCOHOL_CONTENT")) {
                alcoholItem = f.get("validationItemId").asText();
            }
        }
        mvc.perform(post("/api/v1/labels/{id}/review", id).with(user(specialist))
                        .contentType("application/json")
                        .content("{\"overrides\":[{\"validationItemId\":\"" + alcoholItem
                                + "\",\"resolvedStatus\":\"MISMATCH\",\"reviewerNotes\":\"Label says 40%\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_CORRECTION"));
    }

    @Test
    void overrideRequiresJustification() throws Exception {
        String id = submitBourbon(750);
        mvc.perform(post("/api/v1/labels/{id}/override", id).with(user(specialist))
                        .contentType("application/json").content("{\"newStatus\":\"REJECTED\",\"justification\":\"short\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/labels/{id}/override", id).with(user(specialist))
                        .contentType("application/json")
                        .content("{\"newStatus\":\"REJECTED\",\"justification\":\"Brand name differs from COLA record\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void applicantsCannotSeeOtherApplicantsLabels() throws Exception {
        String id = submitBourbon(750);
        mvc.perform(get("/api/v1/labels/{id}", id).with(user(otherApplicant))).andExpect(status().isNotFound());
        mvc.perform(get("/labels/{id}", id).with(user(otherApplicant))).andExpect(status().isNotFound());
    }

    @Test
    void roleGates() throws Exception {
        mvc.perform(get("/api/v1/settings").with(user(applicant))).andExpect(status().isForbidden());
        mvc.perform(get("/settings").with(user(applicant))).andExpect(status().isForbidden());
        mvc.perform(get("/submit").with(user(specialist))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/labels/batch-approve").with(user(applicant))
                        .contentType("application/json").content("{\"labelIds\":[\"x\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedAccess() throws Exception {
        mvc.perform(get("/api/v1/labels")).andExpect(status().isUnauthorized());
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/api/v1/labels").with(httpBasic("specialist@example.gov", SEED_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void spoofedImageIsRejected() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("images", "x.png", "image/png", "<svg onload=alert(1)>".getBytes());
        mvc.perform(multipart("/api/v1/labels").file(fake)
                        .param("beverageType", "WINE").param("containerSizeMl", "750").param("brandName", "X")
                        .with(user(applicant)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void pagesRender() throws Exception {
        String id = submitBourbon(750);
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/").with(user(specialist))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ready to approve")));
        mvc.perform(get("/").param("tab", "all").with(user(specialist))).andExpect(status().isOk());
        mvc.perform(get("/").with(user(applicant))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sample Reserve")));
        mvc.perform(get("/labels/{id}", id).with(user(specialist))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Field comparison")));
        mvc.perform(get("/labels/{id}", id).with(user(applicant))).andExpect(status().isOk());
        mvc.perform(get("/submit").with(user(applicant))).andExpect(status().isOk());
        mvc.perform(get("/submit/batch").with(user(applicant))).andExpect(status().isOk());
        mvc.perform(get("/settings").with(user(specialist))).andExpect(status().isOk());
        mvc.perform(get("/applicants").with(user(specialist))).andExpect(status().isOk());
    }

    @Test
    void webFormsRequireCsrf() throws Exception {
        String id = submitBourbon(750);
        mvc.perform(post("/labels/{id}/reanalyze", id).with(user(specialist))).andExpect(status().isForbidden());
        mvc.perform(post("/labels/{id}/reanalyze", id).with(user(specialist)).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void batchCsvSubmission() throws Exception {
        String csv = """
                beverage_type,container_size_ml,brand_name,images,alcohol_content
                distilled_spirits,750,Batch One,a.png,40%
                wine,200,Batch Two,b.png,13%
                beer,355,Bad Type,a.png,5%
                distilled_spirits,750,Missing Image,zzz.png,40%
                """;
        MockMultipartFile csvFile = new MockMultipartFile("csv", "batch.csv", "text/csv", csv.getBytes());
        mvc.perform(multipart("/submit/batch").file(csvFile).file(png("a.png")).file(png("b.png"))
                        .with(user(applicant)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Batch One")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("was not uploaded")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("beverage_type must be")));
    }
}
