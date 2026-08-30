import { ChangeDetectionStrategy, Component, computed, input, linkedSignal, output } from '@angular/core';

import { DeploymentPackagePublishResponse, DeploymentPackagePublishTarget } from '../../core/artifact-generation.types';
import { DeploymentProfileSummary, FeatureAvailability, OptionAvailability } from '../../core/deployment-profile.types';
import { Feature, ModelMetadata } from '../../core/feature-model.types';
import { GuidedDecision, GuidedDecisionOption, GuidedWorkflowStep, UseCaseTemplate } from '../../core/guided-workflow.types';
import { DEPLOYMENT_TARGETS, REMOTE_DEPLOYMENT_MODE, deploymentTargetFor } from '../shared/deployment-targets';
import {
    ConfiguratorScreen,
    DecisionChangeSummary,
    DecisionOptionToggle,
    LocalizedViolation,
    LocalizedWarning,
    ReviewGroupSummary,
} from '../shared/configurator-view.types';

/** Which of the focused option's three prose lists the impact panel currently shows. */
export type OptionInfoTab = 'outcome' | 'recommended' | 'caveats';

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
    readonly selectedDecisionOptionIds = input.required<ReadonlyMap<string, ReadonlySet<string>>>();
    readonly focusedOption = input<GuidedDecisionOption | undefined>(undefined);
    readonly featureNamesById = input.required<ReadonlyMap<string, string>>();
    readonly changedDecisionSummaries = input.required<DecisionChangeSummary[]>();
    readonly reviewGroups = input.required<ReviewGroupSummary[]>();
    readonly optionAvailabilityById = input.required<ReadonlyMap<string, OptionAvailability>>();
    readonly profileDependentFeatures = input.required<FeatureAvailability[]>();
    readonly reconciliationNote = input<string | undefined>(undefined);
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
    readonly deploymentTargetName = input<string>('');
    readonly publishTarget = input<DeploymentPackagePublishTarget | undefined>(undefined);
    readonly deploymentPackagePublishing = input<boolean>(false);
    readonly deploymentPackagePublishResult = input<DeploymentPackagePublishResponse | undefined>(undefined);
    readonly deploymentPackagePublishErrorMessage = input<string | undefined>(undefined);
    readonly deploymentTargets = DEPLOYMENT_TARGETS;
    readonly selectedDeploymentTarget = computed(() => deploymentTargetFor(this.selectedDeploymentMode()));
    readonly remoteTargetSelected = computed(() => this.selectedDeploymentMode() === REMOTE_DEPLOYMENT_MODE);
    /** Directory-name form of the target name, mirroring the server-side sanitization for the destination preview. */
    readonly sanitizedTargetDirectoryName = computed(() => this.deploymentTargetName().trim().toLowerCase().replace(/[^a-z0-9-]/g, ''));
    /** The publish action needs a configured deployment repository and a routable target name. */
    readonly publishActionAvailable = computed(() => (this.publishTarget()?.configured ?? false) && this.sanitizedTargetDirectoryName().length > 0);
    readonly publishDestination = computed(() => {
        const target = this.publishTarget();
        if (!target?.configured) {
            return '';
        }
        return `${target.repositoryUrl}@${target.branch}/${target.targetDirectoryRoot}/${this.sanitizedTargetDirectoryName()}`;
    });
    readonly publishResultShortSha = computed(() => this.deploymentPackagePublishResult()?.commitSha.slice(0, 7) ?? '');

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
    readonly publishAndDownloadDeploymentPackage = output<void>();
    readonly selectDeploymentMode = output<string>();
    readonly deploymentTargetNameChange = output<string>();

    readonly activeStep = computed<GuidedWorkflowStep | undefined>(() => this.decisionSteps()[this.activeStepIndex()]);

    /** Resets to the first tab whenever the impact panel switches to another option. */
    readonly infoTab = linkedSignal<string, OptionInfoTab>({
        source: () => this.focusedOption()?.id ?? '',
        computation: () => 'outcome',
    });

    /** Blocking findings gate the export, so the review page counts them separately from advisory ones. */
    readonly reviewWarningCount = computed(() => this.localizedWarnings().length);
    readonly reviewNoteCount = computed(() => this.profileDependentFeatures().length + (this.reconciliationNote() ? 1 : 0));
    readonly hasReviewFindings = computed(() => this.localizedViolations().length > 0 || this.reviewWarningCount() > 0 || this.reviewNoteCount() > 0);
    readonly exportBlocked = computed(() => !this.isValid() || this.validationLoading());
    readonly flowStatusClass = computed(() => (this.isValid() ? 'text-success-emphasis' : 'text-danger-emphasis'));

    /** The impact panel shows the focused option without its decision, so selection is looked up by id. */
    readonly isFocusedOptionSelected = computed(() => {
        const focused = this.focusedOption();
        if (!focused) {
            return false;
        }
        for (const optionIds of this.selectedDecisionOptionIds().values()) {
            if (optionIds.has(focused.id)) {
                return true;
            }
        }
        return false;
    });

    onToggleDecisionOption(decision: GuidedDecision, option: GuidedDecisionOption): void {
        if (!this.isOptionAvailable(option)) {
            return;
        }
        this.toggleDecisionOption.emit({ decision, option });
    }

    onSelectInfoTab(tab: OptionInfoTab): void {
        this.infoTab.set(tab);
    }

    onDeploymentTargetNameInput(event: Event): void {
        this.deploymentTargetNameChange.emit((event.target as HTMLInputElement).value);
    }

    isOptionSelected(decision: GuidedDecision, option: GuidedDecisionOption): boolean {
        return this.selectedDecisionOptionIds().get(decision.id)?.has(option.id) ?? false;
    }

    /** True unless the active profile marks the option unavailable; unknown options default to available. */
    isOptionAvailable(option: GuidedDecisionOption): boolean {
        return this.optionAvailabilityById().get(option.id)?.available ?? true;
    }

    /** Bootstrap emphasis utility matching the option's availability, so the state needs no bespoke CSS. */
    availabilityTextClass(option: GuidedDecisionOption): string {
        if (!this.isOptionAvailable(option)) {
            return 'text-danger-emphasis';
        }
        return option.requiresCapabilities.length > 0 ? 'text-warning-emphasis' : 'text-success-emphasis';
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


    /** A decision step is complete once the user has moved past it, and all of them are once review is open. */
    isStepDone(index: number): boolean {
        if (this.screen() === 'review') {
            return true;
        }
        return this.screen() === 'workflow' && index < this.activeStepIndex();
    }

    isStepCurrent(index: number): boolean {
        return this.screen() === 'workflow' && index === this.activeStepIndex();
    }

    trackFeature(_: number, feature: Feature): string {
        return feature.id;
    }
}
