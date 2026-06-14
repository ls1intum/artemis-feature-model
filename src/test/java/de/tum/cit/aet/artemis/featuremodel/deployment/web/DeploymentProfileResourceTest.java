package de.tum.cit.aet.artemis.featuremodel.deployment.web;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.deployment.repository.DeploymentProfileRepository;
import de.tum.cit.aet.artemis.featuremodel.deployment.service.DeploymentProfileService;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelExceptionHandler;
import tools.jackson.databind.ObjectMapper;

class DeploymentProfileResourceTest {

    @TempDir
    Path dataRoot;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DeploymentProfileRepository repository = new DeploymentProfileRepository(new SnapshotProperties(dataRoot.toString(), null), new ObjectMapper());
        DeploymentProfileService service = new DeploymentProfileService(repository);
        mockMvc = MockMvcBuilders.standaloneSetup(new DeploymentProfileResource(service)).setControllerAdvice(new FeatureModelExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter()).build();
    }

    @Test
    void listsProfilesSortedByIdAndFlagsTheDefault() throws Exception {
        // Profiles are sorted by id, so ai-enabled-profile precedes default-teacher-profile.
        mockMvc.perform(get("/api/deployment-profiles")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("ai-enabled-profile"))
                .andExpect(jsonPath("$[0].defaultProfile").value(false)).andExpect(jsonPath("$[1].id").value("default-teacher-profile"))
                .andExpect(jsonPath("$[1].defaultProfile").value(true));
    }

    @Test
    void returnsProfileDetailWithCapabilities() throws Exception {
        mockMvc.perform(get("/api/deployment-profiles/ai-enabled-profile")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ai-enabled-profile")).andExpect(jsonPath("$.providedCapabilities", hasItem("pyris-service")))
                .andExpect(jsonPath("$.parameters['pyris.url']").exists());
    }

    @Test
    void returnsNotFoundForUnknownProfile() throws Exception {
        mockMvc.perform(get("/api/deployment-profiles/missing-profile")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPLOYMENT_PROFILE_NOT_FOUND"));
    }
}
