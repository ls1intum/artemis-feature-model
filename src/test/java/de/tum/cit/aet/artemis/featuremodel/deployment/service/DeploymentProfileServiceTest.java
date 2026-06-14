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
    void listProfilesFlagsTheDefaultProfile() {
        List<DeploymentProfileSummaryDTO> summaries = service().listProfiles();

        assertThat(summaries).filteredOn(summary -> summary.id().equals("default-teacher-profile")).singleElement()
                .satisfies(summary -> assertThat(summary.defaultProfile()).isTrue());
        assertThat(summaries).filteredOn(summary -> summary.id().equals("ai-enabled-profile")).singleElement()
                .satisfies(summary -> assertThat(summary.defaultProfile()).isFalse());
    }

    @Test
    void getProfileDetailReturnsCapabilitiesAndParameters() {
        DeploymentProfileDetailDTO detail = service().getProfileDetail("ai-enabled-profile");

        assertThat(detail.providedCapabilities()).contains("pyris-service", "pyris-secret", "hyperion-service", "athena-service");
        assertThat(detail.parameters()).containsKey("pyris.url");
        assertThat(detail.defaultProfile()).isFalse();
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

        assertThat(service.resolveProfileOrDefault(profiles, null).id()).isEqualTo("default-teacher-profile");
        assertThat(service.resolveProfileOrDefault(profiles, "ai-enabled-profile").id()).isEqualTo("ai-enabled-profile");
    }

    private DeploymentProfileService service() {
        DeploymentProfileRepository repository = new DeploymentProfileRepository(new SnapshotProperties(dataRoot.toString(), null), new ObjectMapper());
        return new DeploymentProfileService(repository);
    }
}
