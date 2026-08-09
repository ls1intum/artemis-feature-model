package de.tum.cit.aet.artemis.featuremodel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class FeatureModelAppTest {

    @Autowired
    private WebApplicationContext applicationContext;

    @Test
    void contextLoads() {
    }

    @Test
    void defaultClasspathModeExposesSafeProvenanceAndNoSnapshotAdministration() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();

        mockMvc.perform(get("/api/feature-model/provenance")).andExpect(status().isOk()).andExpect(jsonPath("$.sourceMode").value("classpath"))
                .andExpect(jsonPath("$.modelId").value("artemis-generated-feature-model")).andExpect(jsonPath("$.snapshotId").doesNotExist());
        mockMvc.perform(get("/api/feature-model/snapshots")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/feature-model/snapshots/anything")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/feature-model/snapshots/import")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/feature-model/snapshots/anything/export")).andExpect(status().isNotFound());
    }
}
