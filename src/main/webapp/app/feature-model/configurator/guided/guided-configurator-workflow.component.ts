import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { DeploymentProfileSummary, FeatureAvailability, OptionAvailability } from '../../core/deployment-profile.types';
import { Feature, ModelMetadata } from '../../core/feature-model.types';
import { GuidedDecision, GuidedDecisionOption, GuidedWorkflowStep, UseCaseTemplate } from '../../core/guided-workflow.types';
import { DEPLOYMENT_TARGETS, deploymentTargetFor } from '../shared/deployment-targets';
import {
    ConfiguratorScreen,
    DecisionChangeSummary,
    DecisionOptionToggle,
    LocalizedViolation,
    LocalizedWarning,
    ReviewGroupSummary,
} from '../shared/configurator-view.types';

@Component({
    selector: 'fm-guided-configurator-workflow',
    standalone: true,
    imports: [],
    changeDetection: ChangeDetectionStrategy.OnPush,
    templateUrl: './guided-configurator-workflow.component.html',
    styleUrl: './guided-configurator-workflow.component.scss',
})
export class GuidedConfiguratorWorkflowComponent {
    /**
     * Temporarily hides the Level 1 artifacts-only export section on the review page; the deployment package with
     * its target picker is the primary export path. Flip to true to restore the section — the template, bindings,
     * and download logic behind it are kept intact.
     */
    protected readonly showArtifactsOnlyExport = false;

    readonly screen = input.required<ConfiguratorScreen>();
    readonly model = input<ModelMetadata | undefined>(undefined);
    readonly selectedCount = input.required<number>();
    readonly templates = input.required<UseCaseTemplate[]>();
    readonly selectedTemplate = input<UseCaseTemplate | undefined>(undefined);
    readonly selectedTemplateId = input<string | undefined>(undefined);
    readonly decisionSteps = input.required<GuidedWorkflowStep[]>();
    readonly activeStepIndex = input.required<number>();
    readonly progressPercent = input.required<number>();
    readonly selectedDecisionOptionIds = input.required<ReadonlyMap<string, ReadonlySet<string>>>();
    readonly focusedOption = input<GuidedDecisionOption | undefined>(undefined);
    readonly featureNamesById = input.required<ReadonlyMap<string, string>>();
    readonly changedDecisionSummaries = input.required<DecisionChangeSummary[]>();
    readonly reviewGroups = input.required<ReviewGroupSummary[]>();
    readonly optionAvailabilityById = input.required<ReadonlyMap<string, OptionAvailability>>();
    readonly profileDependentFeatures = input.required<FeatureAvailability[]>();
    readonly reconciliationNote = input<string | undefined>(undefined);
    readonly workflowWarnings = input.required<string[]>();
    readonly localizedViolations = input.required<LocalizedViolation[]>();
    readonly localizedWarnings = input.required<LocalizedWarning[]>();
    readonly validationLoading = input.required<boolean>();
    readonly validationErrorMessage = input<string | undefined>(undefined);
    readonly hasValidationResult = input.required<boolean>();
    readonly isValid = input.required<boolean>();
    readonly activeProfile = input<DeploymentProfileSummary | undefined>(undefined);
    readonly artifactGenerating = input<boolean>(false);
    readonly artifactErrorMessage = input<string | undefined>(undefined);
    readonly deploymentPackageDownloading = input<boolean>(false);
    readonly deploymentPackageErrorMessage = input<string | undefined>(undefined);
    readonly selectedDeploymentMode = input.required<string>();
    readonly deploymentTargets = DEPLOYMENT_TARGETS;
    readonly selectedDeploymentTarget = computed(() => deploymentTargetFor(this.selectedDeploymentMode()));

    readonly selectTemplate = output<string>();
    readonly startWorkflow = output<void>();
    readonly returnToTemplates = output<void>();
    readonly previousStep = output<void>();
    readonly nextStep = output<void>();
    readonly openReview = output<void>();
    readonly backToWorkflow = output<void>();
    readonly jumpToStep = output<number>();
    readonly focusOption = output<string>();
    readonly toggleDecisionOption = output<DecisionOptionToggle>();
    readonly openTree = output<void>();
    readonly generateArtifacts = output<void>();
    readonly downloadDeploymentPackage = output<void>();
    readonly selectDeploymentMode = output<string>();

    readonly activeStep = computed<GuidedWorkflowStep | undefined>(() => this.decisionSteps()[this.activeStepIndex()]);

    onToggleDecisionOption(decision: GuidedDecision, option: GuidedDecisionOption): void {
        if (!this.isOptionAvailable(option)) {
            return;
        }
        this.toggleDecisionOption.emit({ decision, option });
    }

    isOptionSelected(decision: GuidedDecision, option: GuidedDecisionOption): boolean {
        return this.selectedDecisionOptionIds().get(decision.id)?.has(option.id) ?? false;
    }

    /** True unless the active profile marks the option unavailable; unknown options default to available. */
    isOptionAvailable(option: GuidedDecisionOption): boolean {
        return this.optionAvailabilityById().get(option.id)?.available ?? true;
    }

    optionFeatureNames(option: GuidedDecisionOption): string[] {
        const names = this.featureNamesById();
        return option.selects.map((id) => names.get(id) ?? id);
    }

    /** Builds the regular-user availability message for an option without exposing raw capability ids. */
    optionAvailabilityText(option: GuidedDecisionOption): string {
        const availability = this.optionAvailabilityById().get(option.id);
        if (availability && !availability.available) {
            return availability.teacherReason ?? 'Not available in the current deployment context.';
        }
        if (option.requiresCapabilities.length > 0) {
            return 'Requires deployment setup before it works in a real Artemis instance.';
        }
        return 'Available';
    }

    trackFeature(_: number, feature: Feature): string {
        return feature.id;
    }
}
