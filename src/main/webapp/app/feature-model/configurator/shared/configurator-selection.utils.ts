import { ValidationRelation, ValidationViolation, ValidationWarning } from '../../core/feature-model.types';
import { GuidedDecisionOption } from '../../core/guided-workflow.types';
import { LocalizedFeatureRef, LocalizedRelation, LocalizedViolation, LocalizedWarning } from './configurator-view.types';

export function applyOptionSelection(selection: Set<string>, option: GuidedDecisionOption): void {
    for (const id of option.selects) {
        selection.add(id);
    }
    for (const id of option.deselects) {
        selection.delete(id);
    }
}

export function removeOptionSelection(selection: Set<string>, option: GuidedDecisionOption): void {
    for (const id of option.selects) {
        selection.delete(id);
    }
}

export function cloneDecisionOptionMap(source: ReadonlyMap<string, ReadonlySet<string>>): Map<string, Set<string>> {
    const clone = new Map<string, Set<string>>();
    for (const [decisionId, optionIds] of source) {
        clone.set(decisionId, new Set(optionIds));
    }
    return clone;
}

export function sameStringSet(left: ReadonlySet<string>, right: ReadonlySet<string>): boolean {
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

export function localizeViolation(violation: ValidationViolation, names: ReadonlyMap<string, string>): LocalizedViolation {
    return {
        code: violation.code,
        message: violation.message,
        features: violation.featureIds.map((id) => toLocalizedFeatureRef(id, names)),
        relation: localizeRelation(violation.relation, names),
        suggestion: violation.suggestion,
    };
}

export function localizeWarning(warning: ValidationWarning, names: ReadonlyMap<string, string>): LocalizedWarning {
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
