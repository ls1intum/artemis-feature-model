import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';

import { FeatureModelService } from '../api/feature-model.service';
import { ArtifactGenerationRequest } from '../core/artifact-generation.types';
import { DeploymentProfileSummary, FeatureAvailability, OptionAvailability, WorkflowAvailability } from '../core/deployment-profile.types';
import { Feature, FeatureModelResponse, ValidationResult } from '../core/feature-model.types';
import { GuidedDecision, GuidedDecisionOption, GuidedWorkflow, GuidedWorkflowStep, UseCaseTemplate } from '../core/guided-workflow.types';
import { FeatureModelValidationService } from '../validation/feature-model-validation.service';
import { GuidedConfiguratorWorkflowComponent } from './guided/guided-configurator-workflow.component';
import { ConfiguratorTutorialPanelComponent } from './guided/tutorial/configurator-tutorial-panel.component';
import {
    applyOptionSelection,
    cloneDecisionOptionMap,
    localizeViolation,
    localizeWarning,
    removeOptionSelection,
    sameStringSet,
} from './shared/configurator-selection.utils';
import {
    ConfiguratorScreen,
    DecisionChangeSummary,
    DecisionOptionToggle,
    ReviewGroupSummary,
} from './shared/configurator-view.types';
import {
    CONFIGURATOR_TUTORIAL_STEPS,
    buildConfiguratorTutorialSeenKey,
} from './shared/configurator-tutorial';
import { ConfiguratorTreeComponent } from './tree/configurator-tree.component';

const DEFAULT_ERROR_MESSAGE = 'Failed to load the guided configurator. Please verify that the server is running and try again.';
const DEFAULT_VALIDATION_ERROR_MESSAGE = 'Failed to validate the current selection. Please verify that the server is running and try again.';
const DEFAULT_ARTIFACT_ERROR_MESSAGE = 'Failed to generate artifacts. Please verify that the server is running and try again.';
const DEFAULT_DEPLOYMENT_PACKAGE_ERROR_MESSAGE = 'Failed to generate the local runtime package. Please verify that the server is running and try again.';
const ARTIFACT_PACKAGE_FILE_NAME = 'artemis-feature-model-artifacts.zip';
const DEPLOYMENT_PACKAGE_FILE_NAME = 'artemis-feature-model-deployment-package.zip';
const DEV_IDE_PACKAGE_FILE_NAME = 'artemis-feature-model-dev-ide-package.zip';
const LOCAL_DOCKER_DEPLOYMENT_MODE = 'local-docker';
const DEV_IDE_DEPLOYMENT_MODE = 'dev-ide';

@Component({
    selector: 'fm-feature-model-configurator',
    standalone: true,
    imports: [GuidedConfiguratorWorkflowComponent, ConfiguratorTreeComponent, ConfiguratorTutorialPanelComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    templateUrl: './feature-model-configurator.component.html',
    styleUrl: './feature-model-configurator.component.scss',
})
export class FeatureModelConfiguratorComponent implements OnInit {
    private readonly featureModelService = inject(FeatureModelService);
    private readonly validationService = inject(FeatureModelValidationService);
    private readonly destroyRef = inject(DestroyRef);

    readonly loading = signal<boolean>(true);
    readonly errorMessage = signal<string | undefined>(undefined);
    readonly response = signal<FeatureModelResponse | undefined>(undefined);
    readonly workflow = signal<GuidedWorkflow | undefined>(undefined);
    readonly availability = signal<WorkflowAvailability | undefined>(undefined);
    readonly profileReconciliationNote = signal<string | undefined>(undefined);
    readonly screen = signal<ConfiguratorScreen>('templates');
    readonly previousGuidedScreen = signal<ConfiguratorScreen>('templates');
    readonly activeStepIndex = signal<number>(0);
    readonly selectedTemplateId = signal<string | undefined>(undefined);
    readonly selectedFeatureIds = signal<ReadonlySet<string>>(new Set<string>());
    readonly selectedDecisionOptionIds = signal<ReadonlyMap<string, ReadonlySet<string>>>(new Map());
    readonly templateDecisionOptionIds = signal<ReadonlyMap<string, ReadonlySet<string>>>(new Map());
    readonly focusedOptionId = signal<string | undefined>(undefined);
    readonly validationResult = signal<ValidationResult | undefined>(undefined);
    readonly validationLoading = signal<boolean>(false);
    readonly validationErrorMessage = signal<string | undefined>(undefined);
    readonly artifactGenerating = signal<boolean>(false);
    readonly artifactErrorMessage = signal<string | undefined>(undefined);
    readonly deploymentPackageDownloading = signal<boolean>(false);
    readonly deploymentPackageErrorMessage = signal<string | undefined>(undefined);
    readonly selectedDeploymentMode = signal<string>(LOCAL_DOCKER_DEPLOYMENT_MODE);
    readonly tutorialOpen = signal<boolean>(false);
    readonly tutorialStepIndex = signal<number>(0);
    readonly tutorialSeenKey = signal<string | undefined>(undefined);
    readonly tutorialSteps = CONFIGURATOR_TUTORIAL_STEPS;
    private validationToken = 0;

    readonly model = computed(() => this.response()?.model);
    readonly tree = computed(() => this.response()?.tree ?? null);
    readonly templates = computed(() => this.workflow()?.useCaseTemplates ?? []);
    readonly selectedTemplate = computed<UseCaseTemplate | undefined>(() => {
        const id = this.selectedTemplateId();
        return this.templates().find((template) => template.id === id);
    });
    readonly stepsById = computed<ReadonlyMap<string, GuidedWorkflowStep>>(() => {
        const map = new Map<string, GuidedWorkflowStep>();
        for (const step of this.workflow()?.steps ?? []) {
            map.set(step.id, step);
        }
        return map;
    });
    readonly decisionSteps = computed<GuidedWorkflowStep[]>(() => {
        const template = this.selectedTemplate();
        if (!template) {
            return [];
        }
        return this.visibleDecisionStepsForTemplate(template, this.selectedFeatureIds());
    });
    readonly activeStep = computed<GuidedWorkflowStep | undefined>(() => this.decisionSteps()[this.activeStepIndex()]);
    readonly guidedOptions = computed<GuidedDecisionOption[]>(() => this.decisionSteps().flatMap((step) => step.decisions.flatMap((decision) => decision.options)));
    readonly progressPercent = computed(() => {
        const stepCount = this.decisionSteps().length + 2;
        const current = this.screen() === 'templates' || this.screen() === 'tree' ? 1 : this.screen() === 'review' ? stepCount : this.activeStepIndex() + 2;
        return Math.round((current / stepCount) * 100);
    });
    readonly selectedCount = computed(() => this.selectedFeatureIds().size);
    readonly featureNamesById = computed<ReadonlyMap<string, string>>(() => {
        const map = new Map<string, string>();
        for (const feature of this.response()?.features ?? []) {
            map.set(feature.id, feature.name);
        }
        return map;
    });
    readonly featuresById = computed<ReadonlyMap<string, Feature>>(() => {
        const map = new Map<string, Feature>();
        for (const feature of this.response()?.features ?? []) {
            map.set(feature.id, feature);
        }
        return map;
    });
    readonly selectableFeatureIds = computed<ReadonlySet<string>>(() => {
        const ids = new Set<string>();
        for (const feature of this.response()?.features ?? []) {
            if (feature.selectable) {
                ids.add(feature.id);
            }
        }
        return ids;
    });
    readonly activeProfile = computed<DeploymentProfileSummary | undefined>(() => this.availability()?.activeProfile);
    readonly optionAvailabilityById = computed<ReadonlyMap<string, OptionAvailability>>(() => {
        const map = new Map<string, OptionAvailability>();
        for (const option of this.availability()?.options ?? []) {
            map.set(option.optionId, option);
        }
        return map;
    });
    readonly featureAvailabilityById = computed<ReadonlyMap<string, FeatureAvailability>>(() => {
        const map = new Map<string, FeatureAvailability>();
        for (const feature of this.availability()?.features ?? []) {
            map.set(feature.featureId, feature);
        }
        return map;
    });
    /** Selected features that need deployment setup, surfaced as a neutral review note (unavailable ones first). */
    readonly profileDependentFeatures = computed<FeatureAvailability[]>(() => {
        const selected = this.selectedFeatureIds();
        const dependent = (this.availability()?.features ?? []).filter((feature) => feature.profileDependent && selected.has(feature.featureId));
        return [...dependent].sort((left, right) => Number(left.available) - Number(right.available));
    });
    readonly requiresTargetsBySource = computed<ReadonlyMap<string, ReadonlySet<string>>>(() => {
        const targetsBySource = new Map<string, Set<string>>();
        for (const constraint of this.response()?.constraints ?? []) {
            if (constraint.type !== 'requires' || !constraint.source || !constraint.target) {
                continue;
            }
            const targets = targetsBySource.get(constraint.source) ?? new Set<string>();
            targets.add(constraint.target);
            targetsBySource.set(constraint.source, targets);
        }
        return new Map([...targetsBySource].map(([source, targets]) => [source, new Set(targets)]));
    });
    readonly focusedOption = computed<GuidedDecisionOption | undefined>(() => {
        const focused = this.focusedOptionId();
        const currentStep = this.activeStep();
        if (!currentStep) {
            return undefined;
        }
        const allOptions = currentStep.decisions.flatMap((decision) => decision.options);
        return allOptions.find((option) => option.id === focused) ?? allOptions.find((option) => this.isOptionSelectedById(option.id)) ?? allOptions[0];
    });
    /** Compares the current guided answers with the template baseline for the review page. */
    readonly changedDecisionSummaries = computed<DecisionChangeSummary[]>(() => {
        const summaries: DecisionChangeSummary[] = [];
        for (const step of this.decisionSteps()) {
            for (const decision of step.decisions) {
                const current = this.selectedDecisionOptionIds().get(decision.id) ?? new Set<string>();
                const baseline = this.templateDecisionOptionIds().get(decision.id) ?? new Set<string>();
                if (!sameStringSet(current, baseline)) {
                    summaries.push({
                        decisionId: decision.id,
                        question: decision.question,
                        selectedOptions: decision.options.filter((option) => current.has(option.id)).map((option) => option.label),
                    });
                }
            }
        }
        return summaries;
    });
    readonly reviewGroups = computed<ReviewGroupSummary[]>(() => {
        const selected = this.selectedFeatureIds();
        const features = this.featuresById();
        return [...(this.workflow()?.finalReviewGroups ?? [])]
            .sort((left, right) => left.order - right.order)
            .map((group) => ({
                id: group.id,
                title: group.title,
                features: group.featureIds
                    .map((id) => features.get(id))
                    .filter((feature): feature is Feature => feature !== undefined)
                    .filter((feature) => selected.has(feature.id)),
            }))
            .filter((group) => group.features.length > 0);
    });
    /**
     * Combines template-level warnings with the teacher-facing warnings of the currently selected guided options.
     * Raw capability ids are intentionally not surfaced here; they stay in the advanced tree/debug view.
     */
    readonly workflowWarnings = computed<string[]>(() => {
        const warnings = new Set<string>();
        for (const warning of this.selectedTemplate()?.warnings ?? []) {
            warnings.add(warning);
        }
        for (const option of this.selectedOptions()) {
            for (const warning of option.warnings) {
                warnings.add(warning);
            }
        }
        return [...warnings];
    });
    readonly hasValidationResult = computed(() => this.validationResult() !== undefined);
    readonly isValid = computed(() => this.validationResult()?.valid ?? false);
    readonly violations = computed(() => this.validationResult()?.violations ?? []);
    readonly warnings = computed(() => this.validationResult()?.warnings ?? []);
    /** Extracts feature ids from validation violations so the tree can highlight affected nodes. */
    readonly violationIds = computed<ReadonlySet<string>>(() => {
        const ids = new Set<string>();
        for (const violation of this.violations()) {
            for (const id of violation.featureIds) {
                ids.add(id);
            }
            if (violation.relation && violation.featureIds.length === 0) {
                ids.add(violation.relation.childId);
            }
        }
        return ids;
    });
    /** Extracts feature ids from validation warnings so the tree can highlight affected nodes. */
    readonly warningIds = computed<ReadonlySet<string>>(() => {
        const ids = new Set<string>();
        for (const warning of this.warnings()) {
            for (const id of warning.featureIds) {
                ids.add(id);
            }
        }
        return ids;
    });
    readonly localizedViolations = computed(() => this.violations().map((violation) => localizeViolation(violation, this.featureNamesById())));
    readonly localizedWarnings = computed(() => this.warnings().map((warning) => localizeWarning(warning, this.featureNamesById())));

    ngOnInit(): void {
        forkJoin({
            featureModel: this.featureModelService.loadFeatureModel(),
            guidedWorkflow: this.featureModelService.loadGuidedWorkflow(),
            availability: this.featureModelService.loadWorkflowAvailability(),
        })
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: ({ featureModel, guidedWorkflow, availability }) => this.handleLoaded(featureModel, guidedWorkflow, availability),
                error: (error: Error) => this.handleError(error),
            });
    }

    /** Applies a template and infers which guided options should appear selected from that baseline. */
    onSelectTemplate(templateId: string): void {
        const template = this.templates().find((candidate) => candidate.id === templateId);
        if (!template) {
            return;
        }
        const selected = this.initialSelectionForTemplate(template);
        const optionIdsByDecision = this.inferSelectedDecisionOptions(selected, this.visibleDecisionStepsForTemplate(template, selected));
        this.profileReconciliationNote.set(undefined);
        this.selectedTemplateId.set(template.id);
        this.selectedFeatureIds.set(selected);
        this.selectedDecisionOptionIds.set(cloneDecisionOptionMap(optionIdsByDecision));
        this.templateDecisionOptionIds.set(cloneDecisionOptionMap(optionIdsByDecision));
        this.activeStepIndex.set(0);
        this.focusedOptionId.set(this.firstOptionIdForCurrentFlow(optionIdsByDecision));
        this.runValidation();
    }

    onStartWorkflow(): void {
        this.screen.set('workflow');
        this.previousGuidedScreen.set('workflow');
        this.activeStepIndex.set(0);
        this.focusedOptionId.set(this.firstOptionIdForCurrentStep());
    }

    onReturnToTemplates(): void {
        this.screen.set('templates');
        this.previousGuidedScreen.set('templates');
    }

    onPreviousStep(): void {
        if (this.activeStepIndex() === 0) {
            this.onReturnToTemplates();
            return;
        }
        this.activeStepIndex.update((value) => value - 1);
        this.focusedOptionId.set(this.firstOptionIdForCurrentStep());
    }

    onNextStep(): void {
        if (this.activeStepIndex() >= this.decisionSteps().length - 1) {
            this.onOpenReview();
            return;
        }
        this.activeStepIndex.update((value) => value + 1);
        this.focusedOptionId.set(this.firstOptionIdForCurrentStep());
    }

    onOpenReview(): void {
        this.screen.set('review');
        this.previousGuidedScreen.set('review');
    }

    onBackToWorkflow(): void {
        this.screen.set('workflow');
        this.previousGuidedScreen.set('workflow');
        if (this.activeStepIndex() >= this.decisionSteps().length) {
            this.activeStepIndex.set(Math.max(this.decisionSteps().length - 1, 0));
        }
    }

    onJumpToStep(index: number): void {
        this.screen.set('workflow');
        this.previousGuidedScreen.set('workflow');
        this.activeStepIndex.set(index);
        this.focusedOptionId.set(this.firstOptionIdForCurrentStep());
    }

    onOpenTree(): void {
        if (this.screen() !== 'tree') {
            this.previousGuidedScreen.set(this.screen());
        }
        this.screen.set('tree');
    }

    onCloseTree(): void {
        const previous = this.previousGuidedScreen();
        this.screen.set(previous === 'tree' ? 'templates' : previous);
    }

    onOpenTutorial(): void {
        this.tutorialStepIndex.set(0);
        this.tutorialOpen.set(true);
    }

    onPreviousTutorialStep(): void {
        this.tutorialStepIndex.update((value) => Math.max(value - 1, 0));
    }

    onNextTutorialStep(): void {
        this.tutorialStepIndex.update((value) => Math.min(value + 1, this.tutorialSteps.length - 1));
    }

    onSkipTutorial(): void {
        this.markTutorialSeen();
        this.tutorialOpen.set(false);
    }

    onFinishTutorial(): void {
        this.markTutorialSeen();
        this.tutorialOpen.set(false);
    }

    onFocusOption(optionId: string): void {
        this.focusedOptionId.set(optionId);
    }

    /** Keeps guided option state and raw feature selection in sync when the user changes an answer. */
    onToggleDecisionOption(change: DecisionOptionToggle): void {
        const { decision, option } = change;
        this.focusedOptionId.set(option.id);
        const optionIdsByDecision = cloneDecisionOptionMap(this.selectedDecisionOptionIds());
        const currentOptionIds = new Set(optionIdsByDecision.get(decision.id) ?? []);
        const nextSelection = new Set(this.selectedFeatureIds());
        const isSelected = currentOptionIds.has(option.id);

        if (isSelected) {
            currentOptionIds.delete(option.id);
            removeOptionSelection(nextSelection, option);
        } else {
            if (decision.selectionMode === 'single') {
                for (const previousOptionId of currentOptionIds) {
                    const previousOption = decision.options.find((candidate) => candidate.id === previousOptionId);
                    if (previousOption) {
                        removeOptionSelection(nextSelection, previousOption);
                    }
                }
                currentOptionIds.clear();
            }
            currentOptionIds.add(option.id);
            applyOptionSelection(nextSelection, option);
        }

        optionIdsByDecision.set(decision.id, currentOptionIds);
        this.removeHiddenOptionSelections(nextSelection, optionIdsByDecision);
        this.selectedDecisionOptionIds.set(optionIdsByDecision);
        this.selectedFeatureIds.set(nextSelection);
        this.runValidation();
    }

    /**
     * Rebuilds guided answers after the advanced tree edits the underlying feature selection directly. Features the
     * active profile does not support are reconciled out with a clear note instead of being silently configured.
     */
    onReplaceSelection(nextSelection: ReadonlySet<string>): void {
        const { selection, removed } = this.withoutCapabilityUnavailableFeatures(nextSelection);
        const inferred = cloneDecisionOptionMap(this.inferSelectedDecisionOptions(selection));
        this.removeHiddenOptionSelections(selection, inferred);
        this.selectedFeatureIds.set(selection);
        this.selectedDecisionOptionIds.set(inferred);
        this.focusedOptionId.set(this.firstOptionIdForCurrentFlow(inferred));
        if (removed.length > 0) {
            const profileName = this.activeProfile()?.name ?? 'the selected profile';
            this.profileReconciliationNote.set(
                `${removed.join(', ')} ${removed.length === 1 ? 'is' : 'are'} not available in ${profileName} and ${
                    removed.length === 1 ? 'was' : 'were'
                } removed from the selection.`,
            );
        }
        this.runValidation();
    }

    /** Generates and downloads the artifact ZIP package for the current valid selection, with no preview step. */
    onGenerateArtifacts(): void {
        if (!this.isValid()) {
            return;
        }
        this.artifactGenerating.set(true);
        this.artifactErrorMessage.set(undefined);
        this.featureModelService
            .downloadArtifacts({ selectedFeatureIds: [...this.selectedFeatureIds()] })
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (blob) => {
                    this.saveBlob(blob, ARTIFACT_PACKAGE_FILE_NAME);
                    this.artifactGenerating.set(false);
                },
                error: (error: Error) => {
                    this.artifactErrorMessage.set(this.artifactErrorText(error));
                    this.artifactGenerating.set(false);
                },
            });
    }

    /** Switches the review-page deployment target; the default local Docker target keeps today's request shape. */
    onSelectDeploymentMode(deploymentMode: string): void {
        this.selectedDeploymentMode.set(deploymentMode);
        this.deploymentPackageErrorMessage.set(undefined);
    }

    /**
     * Downloads the deployment package ZIP for the current valid selection and the selected deployment target. The
     * default local Docker target sends the request without a deployment mode, preserving the pre-mode-axis behavior;
     * any other target is sent explicitly as `deploymentMode`.
     */
    onDownloadDeploymentPackage(): void {
        if (!this.isValid()) {
            return;
        }
        const deploymentMode = this.selectedDeploymentMode();
        const request: ArtifactGenerationRequest = { selectedFeatureIds: [...this.selectedFeatureIds()] };
        if (deploymentMode !== LOCAL_DOCKER_DEPLOYMENT_MODE) {
            request.deploymentMode = deploymentMode;
        }
        const fileName = deploymentMode === DEV_IDE_DEPLOYMENT_MODE ? DEV_IDE_PACKAGE_FILE_NAME : DEPLOYMENT_PACKAGE_FILE_NAME;
        this.deploymentPackageDownloading.set(true);
        this.deploymentPackageErrorMessage.set(undefined);
        this.featureModelService
            .downloadDeploymentPackage(request)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (blob) => {
                    this.saveBlob(blob, fileName);
                    this.deploymentPackageDownloading.set(false);
                },
                error: (error: Error) => {
                    this.deploymentPackageErrorMessage.set(this.deploymentPackageErrorText(error));
                    this.deploymentPackageDownloading.set(false);
                },
            });
    }

    /** Resolves a user-facing artifact error message, falling back to a default when none is present. */
    private artifactErrorText(error: Error): string {
        const message = error?.message?.trim();
        return message && message.length > 0 ? message : DEFAULT_ARTIFACT_ERROR_MESSAGE;
    }

    /** Resolves a user-facing deployment package error message, falling back to a default when none is present. */
    private deploymentPackageErrorText(error: Error): string {
        const message = error?.message?.trim();
        return message && message.length > 0 ? message : DEFAULT_DEPLOYMENT_PACKAGE_ERROR_MESSAGE;
    }

    /** Triggers a browser download for a generated blob without persisting it anywhere on the server. */
    private saveBlob(blob: Blob, fileName: string): void {
        if (typeof document === 'undefined' || typeof URL === 'undefined' || typeof URL.createObjectURL !== 'function') {
            return;
        }
        const objectUrl = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = objectUrl;
        anchor.download = fileName;
        anchor.click();
        URL.revokeObjectURL(objectUrl);
    }

    private handleLoaded(response: FeatureModelResponse, workflow: GuidedWorkflow, availability: WorkflowAvailability): void {
        this.response.set(response);
        this.workflow.set(workflow);
        this.availability.set(availability);
        this.errorMessage.set(undefined);
        this.loading.set(false);
        this.onSelectTemplate(workflow.workflow.defaultTemplateId);
        this.initializeTutorialState(response, workflow);
    }

    /**
     * Removes features the active deployment context marks unavailable, returning the kept selection and removed display
     * names. With the bundled prototype profile every capability is provided, so this only filters when a maintainer
     * local override restricts capabilities.
     */
    private withoutCapabilityUnavailableFeatures(selection: ReadonlySet<string>): { selection: Set<string>; removed: string[] } {
        const availabilityById = this.featureAvailabilityById();
        const kept = new Set<string>();
        const removed: string[] = [];
        for (const id of selection) {
            const availability = availabilityById.get(id);
            if (availability && availability.missingCapabilities.length > 0) {
                removed.push(availability.featureName);
            } else {
                kept.add(id);
            }
        }
        return { selection: kept, removed };
    }

    /** Opens the tutorial once per model/workflow version by using a versioned browser-storage key. */
    private initializeTutorialState(response: FeatureModelResponse, workflow: GuidedWorkflow): void {
        const key = buildConfiguratorTutorialSeenKey(response.model, workflow.workflow);
        this.tutorialSeenKey.set(key);
        this.tutorialStepIndex.set(0);
        this.tutorialOpen.set(!this.isTutorialSeen(key));
    }

    /**
     * Creates the starting feature set for a template as a delta over features addressed by the guided workflow.
     * Guided functional features keep the existing preset semantics, while technical features retain their
     * current/default state. Features missing a deployment capability are dropped.
     */
    private initialSelectionForTemplate(template: UseCaseTemplate): ReadonlySet<string> {
        const selected = this.templateSelectionBaseline();
        if (template.selectedFeatureIds.length > 0) {
            this.replaceFunctionalTemplateSelection(selected, template.selectedFeatureIds);
        }
        for (const id of template.deselectedFeatureIds) {
            selected.delete(id);
        }
        for (const id of [...selected]) {
            if (!this.selectableFeatureIds().has(id)) {
                selected.delete(id);
            }
        }
        return this.withoutCapabilityUnavailableFeatures(selected).selection;
    }

    /** Starts a template from model defaults while retaining the current state of technical features. */
    private templateSelectionBaseline(): Set<string> {
        const baseline = new Set(this.response()?.defaultSelectedFeatureIds ?? []);
        const current = this.selectedFeatureIds();
        for (const feature of this.response()?.features ?? []) {
            if (!feature.selectable || feature.category !== 'technical') {
                continue;
            }
            if (current.has(feature.id)) {
                baseline.add(feature.id);
            } else if (current.size > 0) {
                baseline.delete(feature.id);
            }
        }
        return baseline;
    }

    /** Replaces the guided functional portion of a template while leaving technical selections unchanged. */
    private replaceFunctionalTemplateSelection(selection: Set<string>, selectedFeatureIds: string[]): void {
        for (const feature of this.response()?.features ?? []) {
            if (feature.selectable && feature.category !== 'technical') {
                selection.delete(feature.id);
            }
        }
        for (const id of selectedFeatureIds) {
            selection.add(id);
        }
    }

    /** Resolves the workflow steps that should be shown for a template, sorted by authored order. */
    private decisionStepsForTemplate(template: UseCaseTemplate): GuidedWorkflowStep[] {
        const workflow = this.workflow();
        if (!workflow) {
            return [];
        }
        const ids = template.recommendedStepIds.length > 0 ? template.recommendedStepIds : workflow.steps.map((step) => step.id);
        const steps = ids.map((id) => this.stepsById().get(id)).filter((step): step is GuidedWorkflowStep => Boolean(step));
        return steps.filter((step) => step.decisions.length > 0).sort((left, right) => left.order - right.order);
    }

    /** Hides options that would select a feature while leaving one of its requires constraints unsatisfied. */
    private visibleDecisionStepsForTemplate(template: UseCaseTemplate, selectedFeatureIds: ReadonlySet<string>): GuidedWorkflowStep[] {
        return this.decisionStepsForTemplate(template)
            .map((step) => ({
                ...step,
                decisions: step.decisions
                    .map((decision) => ({
                        ...decision,
                        options: decision.options.filter((option) => this.isOptionVisible(option, selectedFeatureIds)),
                    }))
                    .filter((decision) => decision.options.length > 0),
            }))
            .filter((step) => step.decisions.length > 0);
    }

    /** Infers guided option checkmarks from feature ids, used for template defaults and tree-driven edits. */
    private inferSelectedDecisionOptions(
        selectedFeatureIds: ReadonlySet<string>,
        steps: readonly GuidedWorkflowStep[] = this.decisionSteps(),
    ): ReadonlyMap<string, ReadonlySet<string>> {
        const map = new Map<string, ReadonlySet<string>>();
        for (const step of steps) {
            for (const decision of step.decisions) {
                const selectedOptions = new Set<string>();
                for (const option of decision.options) {
                    const selectsMatch = option.selects.length > 0 && option.selects.every((featureId) => selectedFeatureIds.has(featureId));
                    const deselectsMatch = option.deselects.every((featureId) => !selectedFeatureIds.has(featureId));
                    if (selectsMatch && deselectsMatch) {
                        selectedOptions.add(option.id);
                    }
                }
                map.set(decision.id, selectedOptions);
            }
        }
        return map;
    }

    /** Returns the selected option objects so downstream summaries can read warnings and capability metadata. */
    private selectedOptions(): GuidedDecisionOption[] {
        const selected = this.selectedDecisionOptionIds();
        const options: GuidedDecisionOption[] = [];
        for (const step of this.decisionSteps()) {
            for (const decision of step.decisions) {
                const selectedIds = selected.get(decision.id) ?? new Set<string>();
                for (const option of decision.options) {
                    if (selectedIds.has(option.id)) {
                        options.push(option);
                    }
                }
            }
        }
        return options;
    }

    /** Evaluates visibility against the selection that would exist after applying the option. */
    private isOptionVisible(option: GuidedDecisionOption, selectedFeatureIds: ReadonlySet<string>): boolean {
        const candidateSelection = new Set(selectedFeatureIds);
        applyOptionSelection(candidateSelection, option);
        for (const sourceFeatureId of option.selects) {
            const requiredTargetIds = this.requiresTargetsBySource().get(sourceFeatureId) ?? new Set<string>();
            for (const targetFeatureId of requiredTargetIds) {
                if (!candidateSelection.has(targetFeatureId)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Drops selected guided options that became hidden after another option removed a required feature. */
    private removeHiddenOptionSelections(nextSelection: Set<string>, optionIdsByDecision: Map<string, ReadonlySet<string>>): void {
        const template = this.selectedTemplate();
        if (!template) {
            return;
        }
        for (const step of this.decisionStepsForTemplate(template)) {
            for (const decision of step.decisions) {
                const selectedIds = new Set(optionIdsByDecision.get(decision.id) ?? []);
                for (const option of decision.options) {
                    if (selectedIds.has(option.id) && !this.isOptionVisible(option, nextSelection)) {
                        selectedIds.delete(option.id);
                        removeOptionSelection(nextSelection, option);
                    }
                }
                optionIdsByDecision.set(decision.id, selectedIds);
            }
        }
    }

    /** Chooses a sensible focused option after a full-flow selection change. */
    private firstOptionIdForCurrentFlow(optionIdsByDecision: ReadonlyMap<string, ReadonlySet<string>>): string | undefined {
        for (const step of this.decisionSteps()) {
            for (const decision of step.decisions) {
                const selectedId = optionIdsByDecision.get(decision.id)?.values().next().value;
                if (selectedId) {
                    return selectedId;
                }
                if (decision.options[0]) {
                    return decision.options[0].id;
                }
            }
        }
        return undefined;
    }

    /** Chooses the selected option, or first available option, for the active guided step. */
    private firstOptionIdForCurrentStep(): string | undefined {
        const step = this.activeStep();
        if (!step) {
            return undefined;
        }
        for (const decision of step.decisions) {
            const selectedId = this.selectedDecisionOptionIds().get(decision.id)?.values().next().value;
            if (selectedId) {
                return selectedId;
            }
            if (decision.options[0]) {
                return decision.options[0].id;
            }
        }
        return undefined;
    }

    private isOptionSelectedById(optionId: string): boolean {
        for (const optionIds of this.selectedDecisionOptionIds().values()) {
            if (optionIds.has(optionId)) {
                return true;
            }
        }
        return false;
    }

    /** Validates the current selection and ignores stale responses from superseded validation requests. */
    private runValidation(): void {
        const token = ++this.validationToken;
        this.validationLoading.set(true);
        this.validationErrorMessage.set(undefined);
        // A selection change clears any stale artifact/package error from a previous attempt.
        this.artifactErrorMessage.set(undefined);
        this.deploymentPackageErrorMessage.set(undefined);
        this.validationService
            .validateSelection(this.selectedFeatureIds())
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (result) => {
                    if (token !== this.validationToken) {
                        return;
                    }
                    this.validationResult.set(result);
                    this.validationLoading.set(false);
                },
                error: (error: Error) => {
                    if (token !== this.validationToken) {
                        return;
                    }
                    const message = error?.message?.trim();
                    this.validationErrorMessage.set(message && message.length > 0 ? message : DEFAULT_VALIDATION_ERROR_MESSAGE);
                    this.validationLoading.set(false);
                },
            });
    }

    private markTutorialSeen(): void {
        const key = this.tutorialSeenKey();
        if (!key) {
            return;
        }
        const storage = this.tutorialStorage();
        if (!storage) {
            return;
        }
        try {
            storage.setItem(key, 'true');
        } catch {
            // Browser storage can be disabled; the tutorial remains usable without persistence.
        }
    }

    private isTutorialSeen(key: string): boolean {
        const storage = this.tutorialStorage();
        if (!storage) {
            return false;
        }
        try {
            return storage.getItem(key) === 'true';
        } catch {
            return false;
        }
    }

    /** Safely accesses localStorage for browsers that block storage or for non-browser render contexts. */
    private tutorialStorage(): Storage | undefined {
        if (typeof window === 'undefined') {
            return undefined;
        }
        try {
            return window.localStorage;
        } catch {
            return undefined;
        }
    }

    private handleError(error: Error): void {
        const message = error?.message?.trim();
        this.errorMessage.set(message && message.length > 0 ? message : DEFAULT_ERROR_MESSAGE);
        this.loading.set(false);
    }
}
