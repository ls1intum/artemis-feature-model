package de.tum.cit.aet.artemis.featuremodel.catalog.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.FeatureModelSourceMode;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelBundle;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelProvenance;

class RuntimeProvenanceResourceTest {

    @Test
    void returnsExactSnapshotIdentityWithoutPathsOrSecrets() throws Exception {
        RuntimeFeatureModelProvenance provenance = new RuntimeFeatureModelProvenance(FeatureModelSourceMode.SNAPSHOT, "generated-model", "2.0.0",
                "generated-abc", "sha256:snapshot", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "sha256:manifest",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "0.3.0");
        RuntimeFeatureModelBundle bundle = new RuntimeFeatureModelBundle(null, null, null, provenance, null);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RuntimeProvenanceResource(bundle))
                .setMessageConverters(new JacksonJsonHttpMessageConverter()).build();

        mockMvc.perform(get("/api/feature-model/provenance")).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sourceMode").value("snapshot")).andExpect(jsonPath("$.modelId").value("generated-model"))
                .andExpect(jsonPath("$.modelVersion").value("2.0.0")).andExpect(jsonPath("$.snapshotId").value("generated-abc"))
                .andExpect(jsonPath("$.snapshotDigest").value("sha256:snapshot"))
                .andExpect(jsonPath("$.artemisCommit").value("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .andExpect(jsonPath("$.manifestDigest").value("sha256:manifest"))
                .andExpect(jsonPath("$.featureModelRepositoryCommit").value("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
                .andExpect(jsonPath("$.extractorVersion").value("0.3.0")).andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/opt/"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }
}
