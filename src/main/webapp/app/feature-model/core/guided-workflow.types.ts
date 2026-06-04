/**
 * Wire-format types for the guided workflow JSON prepared for the Phase 2
 * Configurator redesign. The workflow maps user-facing decisions to canonical
 * feature ids from `/api/feature-model`.
 */

export type GuidedSelectionMode = 'single' | 'multiple' | (string & {});

export interface GuidedWorkflow {
    workflow: GuidedWorkflowMetadata;
    useCaseTemplates: UseCaseTemplate[];
    steps: GuidedWorkflowStep[];
    finalReviewGroups: FinalReviewGroup[];
}

export interface GuidedWorkflowMetadata {
    id: string;
    name: string;
    version: string;
    featureModelId: string;
    featureModelVersion: string;
    defaultTemplateId: string;
}

export interface UseCaseTemplate {
    id: string;
    label: string;
    description: string;
    selectedFeatureIds: string[];
    deselectedFeatureIds: string[];
    recommendedStepIds: string[];
    consequences: string[];
    warnings: string[];
}

export interface GuidedWorkflowStep {
    id: string;
    title: string;
    order: number;
    description: string;
    decisions: GuidedDecision[];
}

export interface GuidedDecision {
    id: string;
    question: string;
    description: string;
    selectionMode: GuidedSelectionMode;
    reviewGroupId: string;
    options: GuidedDecisionOption[];
}

export interface GuidedDecisionOption {
    id: string;
    label: string;
    description: string;
    selects: string[];
    deselects: string[];
    requiresCapabilities: string[];
    consequences: string[];
    artifactImpacts: string[];
    warnings: string[];
}

export interface FinalReviewGroup {
    id: string;
    title: string;
    order: number;
    featureIds: string[];
}
