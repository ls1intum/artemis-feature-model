package de.tum.cit.aet.artemis.featuremodel.deployment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.deployment.dto.FeatureAvailabilityDTO;
import de.tum.cit.aet.artemis.featuremodel.deployment.dto.OptionAvailabilityDTO;
import de.tum.cit.aet.artemis.featuremodel.deployment.dto.WorkflowAvailabilityDTO;
import de.tum.cit.aet.artemis.featuremodel.deployment.repository.DeploymentProfileRepository;
import de.tum.cit.aet.artemis.featuremodel.selection.repository.JsonGuidedWorkflowStore;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowAssembler;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowDiagnosticsService;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowService;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

class CapabilityResolutionServiceTest {

    @TempDir
    Path dataRoot;

    @Test
    void bundledProfileMakesEveryGuidedOptionAvailable() {
        WorkflowAvailabilityDTO availability = service().resolveAvailability(null);

        assertThat(availability.activeProfile().id()).isEqualTo("default-artemis-profile");
        assertThat(availability.activeProfile().defaultProfile()).isTrue();

        // The single bundled deployment context provides every capability, so the teacher is never blocked.
        for (String optionId : new String[] { "enable-iris", "enable-hyperion", "enable-athena", "enable-lti", "enable-theia", "enable-apollon", "enable-sharing" }) {
            OptionAvailabilityDTO option = option(availability, optionId);
            assertThat(option.available()).as("option %s is available", optionId).isTrue();
            assertThat(option.missingCapabilities()).as("option %s has no missing capabilities", optionId).isEmpty();
            assertThat(option.teacherReason()).as("available option %s has no reason", optionId).isNull();
        }
    }

    @Test
    void baselineOptionsHaveNoCapabilityRequirement() {
        OptionAvailabilityDTO lecture = option(service().resolveAvailability(null), "enable-lecture-materials");

        assertThat(lecture.available()).isTrue();
        assertThat(lecture.requiredCapabilities()).isEmpty();
    }

    @Test
    void profileDependentFeaturesAreAvailableButStillFlaggedDependent() {
        FeatureAvailabilityDTO iris = feature(service().resolveAvailability(null), "iris");

        assertThat(iris.available()).isTrue();
        assertThat(iris.profileDependent()).isTrue();
        assertThat(iris.requiredCapabilities()).contains("pyris-service", "pyris-secret");
        assertThat(iris.missingCapabilities()).isEmpty();
    }

    @Test
    void availabilityListsTheActiveProfile() {
        WorkflowAvailabilityDTO availability = service().resolveAvailability(null);

        assertThat(availability.availableProfiles()).extracting(profile -> profile.id()).contains("default-artemis-profile");
    }

    @Test
    void localOverrideWithFewerCapabilitiesStillGatesOptions() throws IOException {
        // A maintainer local override that drops AI capabilities should still gate the dependent options and features.
        writeLocalProfile("{ \"id\": \"default-artemis-profile\", \"name\": \"Restricted\", \"providedCapabilities\": [\"default-database\"] }");

        WorkflowAvailabilityDTO availability = service().resolveAvailability(null);

        OptionAvailabilityDTO iris = option(availability, "enable-iris");
        assertThat(iris.available()).isFalse();
        assertThat(iris.missingCapabilities()).contains("pyris-service", "pyris-secret");
        assertThat(iris.teacherReason()).isNotNull().doesNotContain("pyris").doesNotContain("capability");

        FeatureAvailabilityDTO irisFeature = feature(availability, "iris");
        assertThat(irisFeature.available()).isFalse();
        assertThat(irisFeature.teacherReason()).contains("Iris").doesNotContain("pyris");
    }

    private void writeLocalProfile(String json) throws IOException {
        Path directory = dataRoot.resolve("deployment-profiles");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("override.json"), json);
    }

    private OptionAvailabilityDTO option(WorkflowAvailabilityDTO availability, String optionId) {
        return availability.options().stream().filter(option -> option.optionId().equals(optionId)).findFirst().orElseThrow();
    }

    private FeatureAvailabilityDTO feature(WorkflowAvailabilityDTO availability, String featureId) {
        return availability.features().stream().filter(feature -> feature.featureId().equals(featureId)).findFirst().orElseThrow();
    }

    private CapabilityResolutionService service() {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        ObjectMapper objectMapper = new ObjectMapper();
        FeatureModelTreeService treeService = new FeatureModelTreeService();
        JsonFeatureModelStore featureModelStore = new JsonFeatureModelStore(resourceLoader, objectMapper);
        FeatureModelCatalogService catalogService = new FeatureModelCatalogService(featureModelStore, new FeatureModelIntegrityService(), treeService);
        JsonGuidedWorkflowStore workflowStore = new JsonGuidedWorkflowStore(resourceLoader, objectMapper);
        GuidedWorkflowService workflowService = new GuidedWorkflowService(workflowStore, catalogService, new GuidedWorkflowAssembler(),
                new GuidedWorkflowDiagnosticsService());
        DeploymentProfileRepository repository = new DeploymentProfileRepository(new SnapshotProperties(dataRoot.toString(), null), objectMapper);
        DeploymentProfileService profileService = new DeploymentProfileService(repository);
        return new CapabilityResolutionService(catalogService, workflowService, profileService, new GuidedWorkflowDiagnosticsService());
    }
}
