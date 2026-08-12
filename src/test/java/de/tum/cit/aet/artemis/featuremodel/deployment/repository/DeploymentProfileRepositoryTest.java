package de.tum.cit.aet.artemis.featuremodel.deployment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentProfileException;
import tools.jackson.databind.ObjectMapper;

class DeploymentProfileRepositoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path dataRoot;

    @Test
    void loadsTheSingleBundledClasspathProfile() {
        List<DeploymentProfile> profiles = repository().loadProfiles();

        assertThat(profiles).extracting(DeploymentProfile::id).contains("default-artemis-profile");
        DeploymentProfile defaultProfile = profileById(profiles, "default-artemis-profile");
        // The single bundled profile provides every capability the guided workflow references.
        assertThat(defaultProfile.providedCapabilities()).contains("pyris-service", "pyris-secret", "athena-service", "hyperion-service",
                "lti-platform-registration", "theia-service", "sharing-platform-registration", "sharing-secret");
        assertThat(defaultProfile.version()).isEqualTo("2.0.0");
        assertThat(defaultProfile.providedCapabilities()).doesNotContain("default-authentication");
    }

    @Test
    void rejectsALegacyProfileContainingParametersWithAMigrationMessage() throws IOException {
        writeLocalProfile("legacy.json",
                "{ \"id\": \"legacy-profile\", \"name\": \"Legacy Profile\", \"parameters\": { \"artemis.iris.url\": \"https://pyris.example.com\" } }");

        assertThatThrownBy(() -> repository().loadProfiles()).isInstanceOf(DeploymentProfileException.class)
                .satisfies(thrown -> assertThat(((DeploymentProfileException) thrown).getCode()).isEqualTo("DEPLOYMENT_PROFILE_LEGACY_PARAMETERS"))
                .hasMessageContaining("capability manifest").hasMessageContaining("2.0.0");
    }

    @Test
    void loadsProfilesSortedById() {
        List<String> ids = repository().loadProfiles().stream().map(DeploymentProfile::id).toList();

        assertThat(ids).isSorted();
    }

    @Test
    void localProfileOverridesClasspathProfileWithSameId() throws IOException {
        writeLocalProfile("override.json", "{ \"id\": \"default-artemis-profile\", \"name\": \"Local Override Profile\", \"version\": \"9.9.9\" }");

        DeploymentProfile overridden = profileById(repository().loadProfiles(), "default-artemis-profile");

        assertThat(overridden.name()).isEqualTo("Local Override Profile");
        assertThat(overridden.version()).isEqualTo("9.9.9");
    }

    @Test
    void localProfileSupplementsClasspathProfiles() throws IOException {
        writeLocalProfile("integration.json",
                "{ \"id\": \"integration-profile\", \"name\": \"Integration Profile\", \"providedCapabilities\": [\"theia-service\"] }");

        List<DeploymentProfile> profiles = repository().loadProfiles();

        assertThat(profiles).extracting(DeploymentProfile::id).contains("default-artemis-profile", "integration-profile");
        assertThat(profileById(profiles, "integration-profile").providedCapabilities()).containsExactly("theia-service");
    }

    @Test
    void treatsAnAbsentSupportedDeploymentModesFieldAsAllModesSupported() {
        DeploymentProfile defaultProfile = profileById(repository().loadProfiles(), "default-artemis-profile");

        assertThat(defaultProfile.supportedDeploymentModes()).isNull();
        assertThat(defaultProfile.supportsDeploymentMode("local-docker")).isTrue();
        assertThat(defaultProfile.supportsDeploymentMode("dev-ide")).isTrue();
    }

    @Test
    void parsesDeclaredSupportedDeploymentModesAsARestriction() throws IOException {
        writeLocalProfile("docker-only.json",
                "{ \"id\": \"docker-only-profile\", \"name\": \"Docker Only\", \"supportedDeploymentModes\": [\"local-docker\"] }");

        DeploymentProfile profile = profileById(repository().loadProfiles(), "docker-only-profile");

        assertThat(profile.supportedDeploymentModes()).containsExactly("local-docker");
        assertThat(profile.supportsDeploymentMode("local-docker")).isTrue();
        assertThat(profile.supportsDeploymentMode("dev-ide")).isFalse();
    }

    @Test
    void loadsAProfileWithAnUnknownDeploymentModeEntryLeniently() throws IOException {
        writeLocalProfile("future.json",
                "{ \"id\": \"future-profile\", \"name\": \"Future\", \"supportedDeploymentModes\": [\"local-docker\", \"remote-ansible\"] }");

        DeploymentProfile profile = profileById(repository().loadProfiles(), "future-profile");

        // The unknown entry is not a load failure; it stays inert because it never matches a known requested mode.
        assertThat(profile.supportedDeploymentModes()).containsExactly("local-docker", "remote-ansible");
        assertThat(profile.supportsDeploymentMode("local-docker")).isTrue();
    }

    @Test
    void rejectsDuplicateLocalProfileIds() throws IOException {
        writeLocalProfile("first.json", "{ \"id\": \"duplicate-profile\", \"name\": \"First\" }");
        writeLocalProfile("second.json", "{ \"id\": \"duplicate-profile\", \"name\": \"Second\" }");

        assertThatThrownBy(() -> repository().loadProfiles()).isInstanceOf(DeploymentProfileException.class)
                .satisfies(thrown -> assertThat(((DeploymentProfileException) thrown).getCode()).isEqualTo("DEPLOYMENT_PROFILE_DUPLICATE"));
    }

    @Test
    void rejectsInvalidLocalProfileJson() throws IOException {
        writeLocalProfile("broken.json", "{ not valid json ");

        assertThatThrownBy(() -> repository().loadProfiles()).isInstanceOf(DeploymentProfileException.class)
                .satisfies(thrown -> assertThat(((DeploymentProfileException) thrown).getCode()).isEqualTo("DEPLOYMENT_PROFILE_UNREADABLE"));
    }

    private DeploymentProfileRepository repository() {
        return new DeploymentProfileRepository(new SnapshotProperties(dataRoot.toString(), null), objectMapper);
    }

    private DeploymentProfile profileById(List<DeploymentProfile> profiles, String id) {
        return profiles.stream().filter(profile -> profile.id().equals(id)).findFirst().orElseThrow();
    }

    private void writeLocalProfile(String fileName, String json) throws IOException {
        Path directory = dataRoot.resolve("deployment-profiles");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(fileName), json);
    }
}
