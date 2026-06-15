package de.tum.cit.aet.artemis.featuremodel.deployment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.deployment.dto.DeploymentProfileDetailDTO;
import de.tum.cit.aet.artemis.featuremodel.deployment.dto.DeploymentProfileSummaryDTO;
import de.tum.cit.aet.artemis.featuremodel.deployment.repository.DeploymentProfileRepository;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentProfileException;
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
    void getProfileDetailReturnsCapabilitiesAndParameters() {
        DeploymentProfileDetailDTO detail = service().getProfileDetail("default-artemis-profile");

        assertThat(detail.providedCapabilities()).contains("pyris-service", "pyris-secret", "hyperion-service", "athena-service",
                "lti-platform-registration", "theia-service", "sharing-platform-registration");
        assertThat(detail.parameters()).containsKey("pyris.url");
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
        DeploymentProfileRepository repository = new DeploymentProfileRepository(new SnapshotProperties(dataRoot.toString(), null), new ObjectMapper());
        return new DeploymentProfileService(repository);
    }
}
