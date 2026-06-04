import { Feature } from '../../core/feature-model.types';
import { GuidedDecision, GuidedDecisionOption } from '../../core/guided-workflow.types';

export type ConfiguratorScreen = 'templates' | 'workflow' | 'review' | 'tree';

export interface LocalizedFeatureRef {
    id: string;
    name: string;
}

export interface LocalizedRelation {
    parentId: string;
    childId: string;
    parentName: string;
    childName: string;
}

export interface LocalizedViolation {
    code: string;
    message: string;
    features: LocalizedFeatureRef[];
    relation: LocalizedRelation | null;
    suggestion: string | null;
}

export interface LocalizedWarning {
    code: string;
    message: string;
    features: LocalizedFeatureRef[];
    constraintId: string | null;
    suggestion: string | null;
}

export interface ReviewGroupSummary {
    id: string;
    title: string;
    features: Feature[];
}

export interface DecisionChangeSummary {
    decisionId: string;
    question: string;
    selectedOptions: string[];
}

export interface DecisionOptionToggle {
    decision: GuidedDecision;
    option: GuidedDecisionOption;
}
