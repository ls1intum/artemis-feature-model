import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { FeatureModelService } from '../api/feature-model.service';
import {
    Feature,
    FeatureModelResponse,
    ValidationRelation,
    ValidationResult,
    ValidationViolation,
    ValidationWarning,
} from '../core/feature-model.types';
import { GuidedDecision, GuidedDecisionOption, GuidedWorkflow, GuidedWorkflowStep, UseCaseTemplate } from '../core/guided-workflow.types';
import { FeatureModelValidationService } from '../validation/feature-model-validation.service';

const DEFAULT_ERROR_MESSAGE = 'Failed to load the guided configurator. Please verify that the server is running and try again.';
const DEFAULT_VALIDATION_ERROR_MESSAGE = 'Failed to validate the current selection. Please verify that the server is running and try again.';

type ConfiguratorScreen = 'templates' | 'workflow' | 'review';

interface LocalizedFeatureRef {
    id: string;
    name: string;
}

interface LocalizedRelation {
    parentId: string;
    childId: string;
    parentName: string;
    childName: string;
}

interface LocalizedViolation {
    code: string;
    message: string;
    features: LocalizedFeatureRef[];
    relation: LocalizedRelation | null;
    suggestion: string | null;
}

interface LocalizedWarning {
    code: string;
    message: string;
    features: LocalizedFeatureRef[];
    constraintId: string | null;
    suggestion: string | null;
}

interface ReviewGroupSummary {
    id: string;
    title: string;
    features: Feature[];
}

interface DecisionChangeSummary {
    decisionId: string;
    question: string;
    selectedOptions: string[];
}

@Component({
    selector: 'fm-feature-model-configurator',
    standalone: true,
    imports: [RouterLink],
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
    readonly activeStepIndex = signal<number>(0);
    readonly selectedTemplateId = signal<string | undefined>(undefined);
    readonly selectedFeatureIds = signal<ReadonlySet<string>>(new Set<string>());
    readonly templateBaselineFeatureIds = signal<ReadonlySet<string>>(new Set<string>());
    readonly selectedDecisionOptionIds = signal<ReadonlyMap<string, ReadonlySet<string>>>(new Map());
    readonly templateDecisionOptionIds = signal<ReadonlyMap<string, ReadonlySet<string>>>(new Map());
    readonly focusedOptionId = signal<string | undefined>(undefined);
    readonly validationResult = signal<ValidationResult | undefined>(undefined);
    readonly validationLoading = signal<boolean>(false);
    readonly validationErrorMessage = signal<string | undefined>(undefined);
    private validationToken = 0;

    readonly model = computed(() => this.response()?.model);
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
        const current = this.screen() === 'templates' ? 1 : this.screen() === 'review' ? stepCount : this.activeStepIndex() + 2;
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

    readonly selectedFunctionalFeatures = computed<Feature[]>(() => {
        const selected = this.selectedFeatureIds();
        return (this.response()?.features ?? []).filter((feature) => selected.has(feature.id) && feature.category === 'functional');
    });

    readonly impactOption = computed<GuidedDecisionOption | undefined>(() => {
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
    readonly localizedViolations = computed<LocalizedViolation[]>(() => {
        const names = this.featureNamesById();
        return this.violations().map((violation) => localizeViolation(violation, names));
    });
    readonly localizedWarnings = computed<LocalizedWarning[]>(() => {
        const names = this.featureNamesById();
        return this.warnings().map((warning) => localizeWarning(warning, names));
    });

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
        this.templateBaselineFeatureIds.set(new Set(selected));
        this.selectedDecisionOptionIds.set(cloneDecisionOptionMap(optionIdsByDecision));
        this.templateDecisionOptionIds.set(cloneDecisionOptionMap(optionIdsByDecision));
        this.activeStepIndex.set(0);
        this.focusedOptionId.set(this.firstOptionIdForCurrentFlow(optionIdsByDecision));
        this.runValidation();
    }

    onStartWorkflow(): void {
        this.screen.set('workflow');
        this.activeStepIndex.set(0);
        this.focusedOptionId.set(this.firstOptionIdForCurrentStep());
    }

    onReturnToTemplates(): void {
        this.screen.set('templates');
    }

    onPreviousStep(): void {
        if (this.activeStepIndex() === 0) {
            this.screen.set('templates');
            return;
        }
        this.activeStepIndex.update((value) => value - 1);
        this.focusedOptionId.set(this.firstOptionIdForCurrentStep());
    }

    onNextStep(): void {
        if (this.activeStepIndex() >= this.decisionSteps().length - 1) {
            this.screen.set('review');
            return;
        }
        this.activeStepIndex.update((value) => value + 1);
        this.focusedOptionId.set(this.firstOptionIdForCurrentStep());
    }

    onOpenReview(): void {
        this.screen.set('review');
    }

    onBackToWorkflow(): void {
        this.screen.set('workflow');
        if (this.activeStepIndex() >= this.decisionSteps().length) {
            this.activeStepIndex.set(Math.max(this.decisionSteps().length - 1, 0));
        }
    }

    onJumpToStep(index: number): void {
        this.screen.set('workflow');
        this.activeStepIndex.set(index);
        this.focusedOptionId.set(this.firstOptionIdForCurrentStep());
    }

    onFocusOption(optionId: string): void {
        this.focusedOptionId.set(optionId);
    }

    onToggleDecisionOption(decision: GuidedDecision, option: GuidedDecisionOption): void {
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

    isOptionSelected(decision: GuidedDecision, option: GuidedDecisionOption): boolean {
        return this.selectedDecisionOptionIds().get(decision.id)?.has(option.id) ?? false;
    }

    optionFeatureNames(option: GuidedDecisionOption): string[] {
        const names = this.featureNamesById();
        return option.selects.map((id) => names.get(id) ?? id);
    }

    optionAvailabilityText(option: GuidedDecisionOption): string {
        if (option.requiresCapabilities.length > 0) {
            return `Needs profile capability: ${option.requiresCapabilities.join(', ')}`;
        }
        return 'Available in the guided MVP';
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
                const selectedIds = optionIdsByDecision.get(decision.id);
                const selectedId = selectedIds?.values().next().value;
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
            const selectedIds = this.selectedDecisionOptionIds().get(decision.id);
            const selectedId = selectedIds?.values().next().value;
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

function applyOptionSelection(selection: Set<string>, option: GuidedDecisionOption): void {
    for (const id of option.selects) {
        selection.add(id);
    }
    for (const id of option.deselects) {
        selection.delete(id);
    }
}

function removeOptionSelection(selection: Set<string>, option: GuidedDecisionOption): void {
    for (const id of option.selects) {
        selection.delete(id);
    }
}

function cloneDecisionOptionMap(source: ReadonlyMap<string, ReadonlySet<string>>): Map<string, Set<string>> {
    const clone = new Map<string, Set<string>>();
    for (const [decisionId, optionIds] of source) {
        clone.set(decisionId, new Set(optionIds));
    }
    return clone;
}

function sameStringSet(left: ReadonlySet<string>, right: ReadonlySet<string>): boolean {
    if (left.size !== right.size) {
        return false;
    }
    for (const value of left) {
        if (!right.has(value)) {
            return false;
        }
    }
    return true;
}

function localizeViolation(violation: ValidationViolation, names: ReadonlyMap<string, string>): LocalizedViolation {
    return {
        code: violation.code,
        message: violation.message,
        features: violation.featureIds.map((id) => toLocalizedFeatureRef(id, names)),
        relation: localizeRelation(violation.relation, names),
        suggestion: violation.suggestion,
    };
}

function localizeWarning(warning: ValidationWarning, names: ReadonlyMap<string, string>): LocalizedWarning {
    return {
        code: warning.code,
        message: warning.message,
        features: warning.featureIds.map((id) => toLocalizedFeatureRef(id, names)),
        constraintId: warning.constraintId,
        suggestion: warning.suggestion,
    };
}

function localizeRelation(relation: ValidationRelation | null, names: ReadonlyMap<string, string>): LocalizedRelation | null {
    if (!relation) {
        return null;
    }
    return {
        parentId: relation.parentId,
        childId: relation.childId,
        parentName: names.get(relation.parentId) ?? relation.parentId,
        childName: names.get(relation.childId) ?? relation.childId,
    };
}

function toLocalizedFeatureRef(id: string, names: ReadonlyMap<string, string>): LocalizedFeatureRef {
    return { id, name: names.get(id) ?? id };
}
