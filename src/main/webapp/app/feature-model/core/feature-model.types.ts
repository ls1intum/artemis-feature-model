/**
 * Wire-format types for the `/api/feature-model` response. Field nullability matches
 * the Spring Boot Jackson serialization of the backend DTOs: optional source metadata,
 * group types, and the incoming relation on the root node arrive as JSON null rather
 * than missing properties, so they are typed as `T | null` instead of `T | undefined`.
 */

export type FeatureKind = 'root' | 'group' | 'module' | 'feature' | (string & {});
export type DefaultState = 'enabled' | 'disabled' | 'not_applicable' | (string & {});
export type RelationType = 'mandatory' | 'optional' | 'group' | (string & {});
export type GroupType = 'and' | 'or' | 'alternative' | (string & {});

export interface FeatureModelResponse {
    model: ModelMetadata;
    features: Feature[];
    relations: Relation[];
    constraints: Constraint[];
    tree: FeatureTreeNode;
    defaultSelectedFeatureIds: string[];
    warnings: ModelWarning[];
}

export interface ModelMetadata {
    id: string;
    name: string;
    version: string;
}

export interface Feature {
    id: string;
    name: string;
    kind: FeatureKind;
    selectable: boolean;
    description: string | null;
    defaultState: DefaultState | null;
    source: FeatureSource | null;
}

export interface FeatureSource {
    configKey: string | null;
    springProfile: string | null;
    frontendConstant: string | null;
    backendConditionClass: string | null;
    evidence: string[];
}

export interface Relation {
    parentId: string;
    childId: string;
    relationType: RelationType;
    groupType: GroupType | null;
    order: number;
}

export interface Constraint {
    id: string;
    type: string;
    source: string | null;
    target: string | null;
    expression: unknown;
    description: string | null;
}

export interface FeatureTreeNode {
    feature: Feature;
    incomingRelation: IncomingRelation | null;
    children: FeatureTreeNode[];
}

export interface IncomingRelation {
    parentId: string;
    childId: string;
    relationType: RelationType;
    groupType: GroupType | null;
    order: number;
}

export interface ModelWarning {
    code: string;
    message: string;
    featureIds: string[];
    constraintId: string | null;
}

export interface ValidationRequest {
    selectedFeatureIds: string[];
}

export interface ValidationResult {
    valid: boolean;
    normalizedSelection: string[];
    violations: ValidationViolation[];
    warnings: ValidationWarning[];
}

export interface ValidationViolation {
    code: string;
    message: string;
    featureIds: string[];
    relation: ValidationRelation | null;
    suggestion: string | null;
}

export interface ValidationWarning {
    code: string;
    message: string;
    featureIds: string[];
    constraintId: string | null;
    suggestion: string | null;
}

export interface ValidationRelation {
    parentId: string;
    childId: string;
}
