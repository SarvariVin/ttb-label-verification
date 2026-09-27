package gov.ttb.labelverification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.ttb.labelverification.ai.ExtractionResult;
import gov.ttb.labelverification.ai.PipelineMetrics;
import gov.ttb.labelverification.config.AppProperties;
import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.repository.UserRepository;
import gov.ttb.labelverification.security.AppUserPrincipal;
import gov.ttb.labelverification.service.ExtractionService;
import gov.ttb.labelverification.storage.DatabaseImageStorage;
import gov.ttb.labelverification.storage.ImageStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Boots with the Railway profile (on H2) and checks database-backed image storage end to end. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "railway"})
class RailwayProfileIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    UserRepository users;
    @Autowired
    ImageStorage storage;
    @Autowired
    AppProperties properties;

    @MockitoBean
    ExtractionService extraction;

    @Test
    void railwayProfileStoresImagesInTheDatabase() throws Exception {
        assertThat(storage).isInstanceOf(DatabaseImageStorage.class);
        assertThat(properties.ocr().maxConcurrent()).isEqualTo(1);

        when(extraction.extract(anyList(), any(), anyMap())).thenReturn(new ExtractionResult(List.of(), List.of(),
                BeverageType.WINE, 1, "stub", Map.of(), new PipelineMetrics(0, 0, 0, 1, 0, 1, 0, 0, 0)));
        AppUserPrincipal applicant = users.findByEmailIgnoreCase("applicant@example.com").map(AppUserPrincipal::from).orElseThrow();
        AppUserPrincipal specialist = users.findByEmailIgnoreCase("specialist@example.gov").map(AppUserPrincipal::from).orElseThrow();
        byte[] png = Files.readAllBytes(Path.of("test-labels", "quillmoor-chardonnay", "front.png"));

        JsonNode created = json.readTree(mvc.perform(multipart("/api/v1/labels")
                        .file(new MockMultipartFile("images", "front.png", "image/png", png))
                        .param("beverageType", "WINE").param("containerSizeMl", "750").param("brandName", "Quillmoor Cellars")
                        .with(user(applicant)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        JsonNode detail = json.readTree(mvc.perform(get("/api/v1/labels/{id}", created.get("labelId").asText())
                .with(user(specialist))).andReturn().getResponse().getContentAsString());

        byte[] served = mvc.perform(get(detail.get("images").get(0).get("url").asText()).with(user(specialist)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(png);
    }
}
