package de.tum.cit.aet.artemis.featuremodel.deployment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

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
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowService;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

class CapabilityResolutionServiceTest {

    @TempDir
    Path dataRoot;

    @Test
    void defaultProfileDisablesCapabilityGatedOptions() {
        WorkflowAvailabilityDTO availability = service().resolveAvailability(null);

        assertThat(availability.activeProfile().id()).isEqualTo("default-teacher-profile");
        assertThat(availability.activeProfile().defaultProfile()).isTrue();

        OptionAvailabilityDTO iris = option(availability, "enable-iris");
        assertThat(iris.available()).isFalse();
        assertThat(iris.missingCapabilities()).contains("pyris-service", "pyris-secret");
        assertThat(option(availability, "enable-hyperion").available()).isFalse();
        assertThat(option(availability, "enable-athena").available()).isFalse();
        assertThat(option(availability, "enable-lti").available()).isFalse();
        assertThat(option(availability, "enable-theia").available()).isFalse();
        assertThat(option(availability, "enable-sharing").available()).isFalse();
    }

    @Test
    void baselineOptionsStayAvailableUnderDefaultProfile() {
        WorkflowAvailabilityDTO availability = service().resolveAvailability(null);

        OptionAvailabilityDTO lecture = option(availability, "enable-lecture-materials");
        assertThat(lecture.available()).isTrue();
        assertThat(lecture.teacherReason()).isNull();
        assertThat(lecture.missingCapabilities()).isEmpty();
    }

    @Test
    void aiEnabledProfileEnablesAiOptionsButNotIntegrationOptions() {
        WorkflowAvailabilityDTO availability = service().resolveAvailability("ai-enabled-profile");

        assertThat(availability.activeProfile().id()).isEqualTo("ai-enabled-profile");
        assertThat(option(availability, "enable-iris").available()).isTrue();
        assertThat(option(availability, "enable-iris").missingCapabilities()).isEmpty();
        assertThat(option(availability, "enable-hyperion").available()).isTrue();
        assertThat(option(availability, "enable-athena").available()).isTrue();
        // LTI, EduIDE, and Sharing need external infrastructure neither bootstrap profile provides.
        assertThat(option(availability, "enable-lti").available()).isFalse();
        assertThat(option(availability, "enable-theia").available()).isFalse();
    }

    @Test
    void teacherReasonNeverExposesRawCapabilityIds() {
        OptionAvailabilityDTO iris = option(service().resolveAvailability(null), "enable-iris");

        assertThat(iris.teacherReason()).isNotNull().doesNotContain("pyris").doesNotContain("capability");
    }

    @Test
    void featureAvailabilityReflectsProfileForProfileDependentFeatures() {
        WorkflowAvailabilityDTO defaultAvailability = service().resolveAvailability(null);
        FeatureAvailabilityDTO irisDefault = feature(defaultAvailability, "iris");
        assertThat(irisDefault.available()).isFalse();
        assertThat(irisDefault.profileDependent()).isTrue();
        assertThat(irisDefault.missingCapabilities()).contains("pyris-service", "pyris-secret");
        assertThat(irisDefault.teacherReason()).contains("Iris").doesNotContain("pyris");

        FeatureAvailabilityDTO lecture = feature(defaultAvailability, "lecture");
        assertThat(lecture.available()).isTrue();
        assertThat(lecture.profileDependent()).isFalse();

        FeatureAvailabilityDTO irisAi = feature(service().resolveAvailability("ai-enabled-profile"), "iris");
        assertThat(irisAi.available()).isTrue();
    }

    @Test
    void availabilityListsAllSelectableProfiles() {
        WorkflowAvailabilityDTO availability = service().resolveAvailability(null);

        assertThat(availability.availableProfiles()).extracting(profile -> profile.id()).contains("default-teacher-profile", "ai-enabled-profile");
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
        GuidedWorkflowService workflowService = new GuidedWorkflowService(workflowStore, catalogService, new GuidedWorkflowIntegrityService());
        DeploymentProfileRepository repository = new DeploymentProfileRepository(new SnapshotProperties(dataRoot.toString(), null), objectMapper);
        DeploymentProfileService profileService = new DeploymentProfileService(repository);
        return new CapabilityResolutionService(catalogService, workflowService, profileService);
    }
}
