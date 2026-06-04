import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';

import { FeatureModelService } from '../api/feature-model.service';
import { Feature, FeatureModelResponse, ValidationResult } from '../core/feature-model.types';
import { GuidedDecision, GuidedDecisionOption, GuidedWorkflow, GuidedWorkflowStep, UseCaseTemplate } from '../core/guided-workflow.types';
import { FeatureModelValidationService } from '../validation/feature-model-validation.service';
import { GuidedConfiguratorWorkflowComponent } from './guided/guided-configurator-workflow.component';
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
import { ConfiguratorTreeComponent } from './tree/configurator-tree.component';

const DEFAULT_ERROR_MESSAGE = 'Failed to load the guided configurator. Please verify that the server is running and try again.';
const DEFAULT_VALIDATION_ERROR_MESSAGE = 'Failed to validate the current selection. Please verify that the server is running and try again.';

@Component({
    selector: 'fm-feature-model-configurator',
    standalone: true,
    imports: [GuidedConfiguratorWorkflowComponent, ConfiguratorTreeComponent],
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
        const workflow = this.workflow();
        const template = this.selectedTemplate();
        if (!workflow || !template) {
            return [];
        }
        const ids = template.recommendedStepIds.length > 0 ? template.recommendedStepIds : workflow.steps.map((step) => step.id);
        const steps = ids.map((id) => this.stepsById().get(id)).filter((step): step is GuidedWorkflowStep => Boolean(step));
        return steps.filter((step) => step.decisions.length > 0).sort((left, right) => left.order - right.order);
    });
    readonly activeStep = computed<GuidedWorkflowStep | undefined>(() => this.decisionSteps()[this.activeStepIndex()]);
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
    readonly focusedOption = computed<GuidedDecisionOption | undefined>(() => {
        const focused = this.focusedOptionId();
        const currentStep = this.activeStep();
        if (!currentStep) {
            return undefined;
        }
        const allOptions = currentStep.decisions.flatMap((decision) => decision.options);
        return allOptions.find((option) => option.id === focused) ?? allOptions.find((option) => this.isOptionSelectedById(option.id)) ?? allOptions[0];
    });
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
    readonly workflowWarnings = computed<string[]>(() => {
        const warnings = new Set<string>();
        for (const warning of this.selectedTemplate()?.warnings ?? []) {
            warnings.add(warning);
        }
        for (const option of this.selectedOptions()) {
            for (const capability of option.requiresCapabilities) {
                warnings.add(`Requires deployment capability: ${capability}.`);
            }
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
        })
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: ({ featureModel, guidedWorkflow }) => this.handleLoaded(featureModel, guidedWorkflow),
                error: (error: Error) => this.handleError(error),
            });
    }

    onSelectTemplate(templateId: string): void {
        const template = this.templates().find((candidate) => candidate.id === templateId);
        if (!template) {
            return;
        }
        const selected = this.initialSelectionForTemplate(template);
        const optionIdsByDecision = this.inferSelectedDecisionOptions(selected);
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

    onFocusOption(optionId: string): void {
        this.focusedOptionId.set(optionId);
    }

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
        this.selectedDecisionOptionIds.set(optionIdsByDecision);
        this.selectedFeatureIds.set(nextSelection);
        this.runValidation();
    }

    onReplaceSelection(nextSelection: ReadonlySet<string>): void {
        const selected = new Set(nextSelection);
        const inferred = this.inferSelectedDecisionOptions(selected);
        this.selectedFeatureIds.set(selected);
        this.selectedDecisionOptionIds.set(cloneDecisionOptionMap(inferred));
        this.focusedOptionId.set(this.firstOptionIdForCurrentFlow(inferred));
        this.runValidation();
    }

    private handleLoaded(response: FeatureModelResponse, workflow: GuidedWorkflow): void {
        this.response.set(response);
        this.workflow.set(workflow);
        this.errorMessage.set(undefined);
        this.loading.set(false);
        this.onSelectTemplate(workflow.workflow.defaultTemplateId);
    }

    private initialSelectionForTemplate(template: UseCaseTemplate): ReadonlySet<string> {
        const selected = template.selectedFeatureIds.length > 0 ? new Set<string>(template.selectedFeatureIds) : new Set<string>(this.response()?.defaultSelectedFeatureIds ?? []);
        for (const id of template.deselectedFeatureIds) {
            selected.delete(id);
        }
        for (const id of [...selected]) {
            if (!this.selectableFeatureIds().has(id)) {
                selected.delete(id);
            }
        }
        return selected;
    }

    private inferSelectedDecisionOptions(selectedFeatureIds: ReadonlySet<string>): ReadonlyMap<string, ReadonlySet<string>> {
        const map = new Map<string, ReadonlySet<string>>();
        for (const step of this.decisionSteps()) {
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

    private runValidation(): void {
        const token = ++this.validationToken;
        this.validationLoading.set(true);
        this.validationErrorMessage.set(undefined);
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

    private handleError(error: Error): void {
        const message = error?.message?.trim();
        this.errorMessage.set(message && message.length > 0 ? message : DEFAULT_ERROR_MESSAGE);
        this.loading.set(false);
    }
}
