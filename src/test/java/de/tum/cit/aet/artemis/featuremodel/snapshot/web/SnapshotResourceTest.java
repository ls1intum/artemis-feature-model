package de.tum.cit.aet.artemis.featuremodel.snapshot.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.LocalSnapshotRepository;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelExceptionHandler;
import de.tum.cit.aet.artemis.featuremodel.snapshot.SnapshotTestFixtures;
import de.tum.cit.aet.artemis.featuremodel.snapshot.service.SnapshotService;
import tools.jackson.databind.ObjectMapper;

class SnapshotResourceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path dataRoot;

    @TempDir
    Path sourceRoot;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalSnapshotRepository repository = new LocalSnapshotRepository(new SnapshotProperties(dataRoot.toString(), "develop-latest"), objectMapper);
        SnapshotService snapshotService = new SnapshotService(repository, objectMapper, new FeatureModelIntegrityService(), new GuidedWorkflowIntegrityService());
        // Register the Jackson 3 and resource converters so the standalone setup mirrors the runtime Boot converters that
        // read records and write archive downloads.
        mockMvc = MockMvcBuilders.standaloneSetup(new SnapshotResource(snapshotService)).setControllerAdvice(new FeatureModelExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(), new ResourceHttpMessageConverter()).build();
    }

    private Path importedModels() {
        return dataRoot.resolve("imported-models");
    }

    @Test
    void listsImportedSnapshots() throws Exception {
        SnapshotTestFixtures.writeValidSnapshot(importedModels().resolve("develop-latest"));

        mockMvc.perform(get("/api/feature-model/snapshots")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].snapshotId").value("develop-latest")).andExpect(jsonPath("$[0].modelId").value(SnapshotTestFixtures.MODEL_ID))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void returnsSnapshotDetail() throws Exception {
        SnapshotTestFixtures.writeValidSnapshot(importedModels().resolve("develop-latest"));

        mockMvc.perform(get("/api/feature-model/snapshots/develop-latest")).andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotId").value("develop-latest")).andExpect(jsonPath("$.modelFileAvailable").value(true))
                .andExpect(jsonPath("$.reportAvailable").value(true));
    }

    @Test
    void importsSnapshotFromLocalFolder() throws Exception {
        Path source = SnapshotTestFixtures.writeValidSnapshot(sourceRoot.resolve("develop-latest"));
        String body = "{ \"sourcePath\": \"" + source + "\", \"snapshotId\": \"imported\" }";

        mockMvc.perform(post("/api/feature-model/snapshots/import").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.snapshotId").value("imported")).andExpect(jsonPath("$.detail.modelFileAvailable").value(true));
    }

    @Test
    void exportsSnapshotAsZip() throws Exception {
        SnapshotTestFixtures.writeValidSnapshot(importedModels().resolve("develop-latest"));

        mockMvc.perform(get("/api/feature-model/snapshots/develop-latest/export")).andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("Content-Disposition", containsString("develop-latest.zip")));
    }

    @Test
    void returnsNotFoundForUnknownSnapshot() throws Exception {
        mockMvc.perform(get("/api/feature-model/snapshots/missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SNAPSHOT_NOT_FOUND"));
    }

    @Test
    void rejectsImportWithMissingSource() throws Exception {
        String body = "{ \"sourcePath\": \"" + sourceRoot.resolve("absent") + "\" }";

        mockMvc.perform(post("/api/feature-model/snapshots/import").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SNAPSHOT_SOURCE_NOT_FOUND"));
    }
}
