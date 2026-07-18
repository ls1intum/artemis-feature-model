package de.tum.cit.aet.artemis.featuremodel.deployment.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.deployment.dto.DeploymentProfileSummaryDTO;
import de.tum.cit.aet.artemis.featuremodel.deployment.dto.FeatureAvailabilityDTO;
import de.tum.cit.aet.artemis.featuremodel.deployment.dto.OptionAvailabilityDTO;
import de.tum.cit.aet.artemis.featuremodel.deployment.dto.WorkflowAvailabilityDTO;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecision;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecisionOption;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowStep;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowService;

/**
 * Computes profile-aware availability for guided decision options and features.
 *
 * <p>
 * Availability combines the prototype role (teacher), the feature visibility and configurability metadata, the
 * capabilities required by the model features, and the capabilities provided by the active deployment profile.
 * Capabilities are single-source on model features; the option requirements consumed here are the serve-time
 * enrichment's derived union over each option's selected features. The service produces two explanation levels: a
 * readable teacher-facing reason that never exposes raw capability ids, and the exact required and missing capability
 * ids for advanced tree and debug views.
 *
 * <p>
 * The service is read-only and does not mutate the loaded feature model or guided workflow.
 */
@Service
public class CapabilityResolutionService {

    private static final Logger log = LoggerFactory.getLogger(CapabilityResolutionService.class);

    /** Fixed prototype role until authentication exists. */
    private static final String ROLE_TEACHER = "teacher";

    private final FeatureModelCatalogService featureModelCatalogService;

    private final GuidedWorkflowService guidedWorkflowService;

    private final DeploymentProfileService deploymentProfileService;

    /**
     * Creates the capability resolution service.
     *
     * @param featureModelCatalogService service used to load the active feature model.
     * @param guidedWorkflowService service used to load the active guided workflow.
     * @param deploymentProfileService service used to load and resolve deployment profiles.
     */
    public CapabilityResolutionService(FeatureModelCatalogService featureModelCatalogService, GuidedWorkflowService guidedWorkflowService,
            DeploymentProfileService deploymentProfileService) {
        this.featureModelCatalogService = featureModelCatalogService;
        this.guidedWorkflowService = guidedWorkflowService;
        this.deploymentProfileService = deploymentProfileService;
    }

    /**
     * Resolves availability of the active guided workflow and feature model under the requested profile, or the default
     * profile when no id is given.
     *
     * @param profileId requested profile id, or {@code null}/blank for the default profile.
     * @return profile-aware workflow availability.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentProfileException if the profile cannot be resolved.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException if the model or workflow cannot be loaded.
     */
    public WorkflowAvailabilityDTO resolveAvailability(String profileId) {
        List<DeploymentProfile> profiles = deploymentProfileService.loadProfiles();
        DeploymentProfile activeProfile = deploymentProfileService.resolveProfileOrDefault(profiles, profileId);
        String defaultProfileId = deploymentProfileService.resolveDefaultProfileId(profiles);

        FeatureModel model = featureModelCatalogService.loadActiveModel();
        GuidedWorkflow workflow = guidedWorkflowService.getActiveGuidedWorkflow();

        List<OptionAvailabilityDTO> options = resolveOptionAvailability(workflow, activeProfile);
        List<FeatureAvailabilityDTO> features = resolveFeatureAvailability(model, activeProfile);

        log.info("Resolved availability under profile '{}': {} options and {} features evaluated.", activeProfile.id(), options.size(), features.size());
        return new WorkflowAvailabilityDTO(summary(activeProfile, defaultProfileId), summaries(profiles, defaultProfileId), options, features);
    }

    /**
     * Resolves availability of every guided decision option under the active profile.
     *
     * @param workflow active guided workflow.
     * @param activeProfile active deployment profile.
     * @return option availability for every option in the workflow.
     */
    private List<OptionAvailabilityDTO> resolveOptionAvailability(GuidedWorkflow workflow, DeploymentProfile activeProfile) {
        List<OptionAvailabilityDTO> result = new ArrayList<>();
        for (GuidedWorkflowStep step : workflow.steps()) {
            for (GuidedDecision decision : step.decisions()) {
                for (GuidedDecisionOption option : decision.options()) {
                    result.add(optionAvailability(option, activeProfile));
                }
            }
        }
        return result;
    }

    /**
     * Builds availability for a single guided decision option.
     *
     * @param option guided decision option.
     * @param activeProfile active deployment profile.
     * @return option availability.
     */
    private OptionAvailabilityDTO optionAvailability(GuidedDecisionOption option, DeploymentProfile activeProfile) {
        List<String> required = List.copyOf(option.requiresCapabilities());
        List<String> missing = missingCapabilities(required, activeProfile);
        boolean available = missing.isEmpty();
        String teacherReason = available ? null : "This option is not available in the current deployment profile.";
        return new OptionAvailabilityDTO(option.id(), available, required, missing, teacherReason);
    }

    /**
     * Resolves availability of every feature under the active profile. Capabilities are single-source on the model
     * features: guided options no longer carry their own capability copies, so the feature's declared
     * {@code requiresCapabilities} is the complete requirement set.
     *
     * @param model active feature model.
     * @param activeProfile active deployment profile.
     * @return feature availability for every feature in the model.
     */
    private List<FeatureAvailabilityDTO> resolveFeatureAvailability(FeatureModel model, DeploymentProfile activeProfile) {
        List<FeatureAvailabilityDTO> result = new ArrayList<>();
        for (FeatureNode feature : model.features()) {
            result.add(featureAvailability(feature, activeProfile));
        }
        return result;
    }

    /**
     * Builds availability for a single feature.
     *
     * @param feature feature node.
     * @param activeProfile active deployment profile.
     * @return feature availability.
     */
    private FeatureAvailabilityDTO featureAvailability(FeatureNode feature, DeploymentProfile activeProfile) {
        List<String> required = List.copyOf(feature.requiresCapabilities());
        List<String> missing = missingCapabilities(required, activeProfile);
        boolean profileDependent = !required.isEmpty();
        boolean roleAllowed = isVisibleToTeacher(feature) && isConfigurableByTeacher(feature);
        boolean available = roleAllowed && missing.isEmpty();
        String teacherReason = available ? null : featureReason(feature, roleAllowed);
        return new FeatureAvailabilityDTO(feature.id(), feature.name(), available, profileDependent, required, missing, teacherReason);
    }

    /**
     * Returns the required capabilities that the active profile does not provide, preserving order.
     *
     * @param requiredCapabilities capabilities that are required.
     * @param activeProfile active deployment profile.
     * @return missing capabilities.
     */
    private List<String> missingCapabilities(List<String> requiredCapabilities, DeploymentProfile activeProfile) {
        List<String> missing = new ArrayList<>();
        for (String capability : requiredCapabilities) {
            if (!activeProfile.providesCapability(capability)) {
                missing.add(capability);
            }
        }
        return missing;
    }

    /**
     * Checks whether the teacher role may see a feature. An empty visibility list means unrestricted visibility.
     *
     * @param feature feature node.
     * @return true if the teacher role may see the feature.
     */
    private boolean isVisibleToTeacher(FeatureNode feature) {
        return feature.visibleTo().isEmpty() || feature.visibleTo().contains(ROLE_TEACHER);
    }

    /**
     * Checks whether the teacher role may configure a feature. Structural features that nobody configures are treated as
     * configurable so they never appear as role-restricted in the availability summary.
     *
     * @param feature feature node.
     * @return true if the teacher role may configure the feature, or the feature is not user-configurable.
     */
    private boolean isConfigurableByTeacher(FeatureNode feature) {
        return feature.configurableBy().isEmpty() || feature.configurableBy().contains(ROLE_TEACHER);
    }

    /**
     * Builds the teacher-facing reason a feature is unavailable, distinguishing role restrictions from profile gaps.
     *
     * @param feature feature node.
     * @param roleAllowed whether the teacher role may use the feature.
     * @return teacher-facing reason.
     */
    private String featureReason(FeatureNode feature, boolean roleAllowed) {
        if (!roleAllowed) {
            return feature.name() + " is not configurable in the current role.";
        }
        return feature.name() + " is not available in the current deployment profile.";
    }

    /**
     * Builds summaries for all profiles with the default profile flagged.
     *
     * @param profiles loaded profiles.
     * @param defaultProfileId resolved default profile id.
     * @return profile summaries.
     */
    private List<DeploymentProfileSummaryDTO> summaries(List<DeploymentProfile> profiles, String defaultProfileId) {
        List<DeploymentProfileSummaryDTO> summaries = new ArrayList<>();
        for (DeploymentProfile profile : profiles) {
            summaries.add(summary(profile, defaultProfileId));
        }
        return summaries;
    }

    /**
     * Builds a summary for a single profile with the default flag.
     *
     * @param profile profile to summarize.
     * @param defaultProfileId resolved default profile id.
     * @return profile summary.
     */
    private DeploymentProfileSummaryDTO summary(DeploymentProfile profile, String defaultProfileId) {
        return DeploymentProfileSummaryDTO.from(profile, profile.id().equals(defaultProfileId));
    }
}
