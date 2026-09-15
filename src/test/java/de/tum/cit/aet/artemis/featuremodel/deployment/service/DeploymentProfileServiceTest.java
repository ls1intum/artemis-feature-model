package de.tum.cit.aet.artemis.featuremodel.deployment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.deployment.dto.DeploymentProfileDetailDTO;
import de.tum.cit.aet.artemis.featuremodel.deployment.dto.DeploymentProfileSummaryDTO;
import de.tum.cit.aet.artemis.featuremodel.deployment.repository.DeploymentProfileRepository;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentProfileException;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

class DeploymentProfileServiceTest {

    @TempDir
    Path dataRoot;

    @Test
    void listProfilesFlagsTheSingleBundledProfileAsDefault() {
        List<DeploymentProfileSummaryDTO> summaries = service().listProfiles();

        assertThat(summaries).filteredOn(summary -> summary.id().equals("default-artemis-profile")).singleElement()
                .satisfies(summary -> assertThat(summary.defaultProfile()).isTrue());
    }

    @Test
    void getProfileDetailReturnsCapabilitiesWithoutAnyParameters() {
        DeploymentProfileDetailDTO detail = service().getProfileDetail("default-artemis-profile");

        // Derived from the active model: <feature>-service for a non-secret deployment input, <feature>-secret for a secret one.
        assertThat(detail.providedCapabilities()).containsExactly("athena-service", "athena-secret", "atlas-service", "iris-service", "iris-secret",
                "hyperion-service", "hyperion-secret", "deimos-service", "deimos-secret", "theia-service", "apollon-service", "sharing-service",
                "sharing-secret");
        assertThat(detail.providedCapabilities()).doesNotContain("lti-platform-registration", "pyris-service", "default-database");
        assertThat(detail.defaultProfile()).isTrue();
    }

    @Test
    void getProfileDetailFailsForUnknownProfile() {
        assertThatThrownBy(() -> service().getProfileDetail("missing-profile")).isInstanceOf(DeploymentProfileException.class)
                .satisfies(thrown -> assertThat(((DeploymentProfileException) thrown).getCode()).isEqualTo("DEPLOYMENT_PROFILE_NOT_FOUND"));
    }

    @Test
    void resolveProfileOrDefaultUsesDefaultWhenNoIdGiven() {
        DeploymentProfileService service = service();
        List<DeploymentProfile> profiles = service.loadProfiles();

        assertThat(service.resolveProfileOrDefault(profiles, null).id()).isEqualTo("default-artemis-profile");
        assertThat(service.resolveProfileOrDefault(profiles, "default-artemis-profile").id()).isEqualTo("default-artemis-profile");
    }

    private DeploymentProfileService service() {
        ObjectMapper objectMapper = new ObjectMapper();
        DeploymentProfileRepository repository = new DeploymentProfileRepository(new SnapshotProperties(dataRoot.toString(), null), objectMapper);
        FeatureModelCatalogService catalogService = new FeatureModelCatalogService(new JsonFeatureModelStore(new DefaultResourceLoader(), objectMapper),
                new FeatureModelIntegrityService(), new FeatureModelTreeService());
        return new DeploymentProfileService(repository, catalogService);
    }
}
