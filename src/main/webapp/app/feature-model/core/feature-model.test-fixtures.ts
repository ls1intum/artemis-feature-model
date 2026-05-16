import {
    DefaultState,
    Feature,
    FeatureKind,
    FeatureModelResponse,
    FeatureSource,
    FeatureTreeNode,
    GroupType,
    IncomingRelation,
    Relation,
    RelationType,
} from './feature-model.types';

interface FeatureSpec {
    id: string;
    name: string;
    kind: FeatureKind;
    selectable: boolean;
    defaultState: DefaultState;
    description?: string;
    configKey?: string;
    springProfile?: string;
    frontendConstant?: string;
    backendConditionClass?: string;
    evidence?: string[];
}

interface RelationSpec {
    parentId: string;
    childId: string;
    relationType: RelationType;
    groupType?: GroupType;
    order: number;
}

const FEATURE_SPECS: FeatureSpec[] = [
    { id: 'artemis', name: 'Artemis', kind: 'root', selectable: false, defaultState: 'not_applicable' },
    { id: 'teaching-and-content', name: 'Teaching and Content', kind: 'group', selectable: false, defaultState: 'not_applicable' },
    {
        id: 'lecture',
        name: 'Lecture',
        kind: 'module',
        selectable: true,
        defaultState: 'enabled',
        description: 'Optional module controlled by artemis.lecture.enabled; ordinary core deployments enable it by default.',
        configKey: 'artemis.lecture.enabled',
        backendConditionClass: 'LectureEnabled',
        evidence: ['LectureEnabled.java:13,23', 'LectureResource.java:77'],
    },
    {
        id: 'tutorialgroup',
        name: 'TutorialGroup',
        kind: 'module',
        selectable: true,
        defaultState: 'enabled',
        configKey: 'artemis.tutorialgroup.enabled',
        backendConditionClass: 'TutorialGroupEnabled',
    },
    { id: 'course-workflow', name: 'Course Workflow', kind: 'module', selectable: true, defaultState: 'enabled' },
    { id: 'communication', name: 'Communication', kind: 'module', selectable: true, defaultState: 'enabled' },
    { id: 'exercise-system', name: 'Exercise System', kind: 'group', selectable: false, defaultState: 'not_applicable' },
    { id: 'exercise-common', name: 'Exercise (Common / Umbrella Layer)', kind: 'module', selectable: true, defaultState: 'enabled' },
    { id: 'programming', name: 'Programming', kind: 'module', selectable: true, defaultState: 'enabled' },
    { id: 'quiz', name: 'Quiz', kind: 'module', selectable: true, defaultState: 'enabled' },
    {
        id: 'text',
        name: 'Text',
        kind: 'module',
        selectable: true,
        defaultState: 'enabled',
        configKey: 'artemis.text.enabled',
        frontendConstant: 'MODULE_FEATURE_TEXT',
    },
    {
        id: 'modeling',
        name: 'Modeling',
        kind: 'module',
        selectable: true,
        defaultState: 'enabled',
        configKey: 'artemis.modeling.enabled',
        frontendConstant: 'MODULE_FEATURE_MODELING',
    },
    {
        id: 'file-upload',
        name: 'File Upload',
        kind: 'module',
        selectable: true,
        defaultState: 'enabled',
        configKey: 'artemis.file-upload.enabled',
        frontendConstant: 'MODULE_FEATURE_FILEUPLOAD',
    },
    { id: 'assessment-and-integrity', name: 'Assessment and Integrity', kind: 'group', selectable: false, defaultState: 'not_applicable' },
    { id: 'exam', name: 'Exam', kind: 'module', selectable: true, defaultState: 'enabled', configKey: 'artemis.exam.enabled' },
    { id: 'plagiarism', name: 'Plagiarism', kind: 'module', selectable: true, defaultState: 'enabled', configKey: 'artemis.plagiarism.enabled' },
    { id: 'athena', name: 'Athena', kind: 'module', selectable: true, defaultState: 'disabled', springProfile: 'athena' },
    { id: 'adaptive-learning-and-ai', name: 'Adaptive Learning and AI', kind: 'group', selectable: false, defaultState: 'not_applicable' },
    { id: 'atlas', name: 'Atlas', kind: 'module', selectable: true, defaultState: 'enabled', configKey: 'artemis.atlas.enabled' },
    { id: 'iris', name: 'Iris', kind: 'module', selectable: true, defaultState: 'disabled', configKey: 'artemis.iris.enabled' },
    { id: 'hyperion', name: 'Hyperion', kind: 'module', selectable: true, defaultState: 'disabled', configKey: 'artemis.hyperion.enabled' },
    { id: 'platform-integrations', name: 'Platform Integrations', kind: 'group', selectable: false, defaultState: 'not_applicable' },
    { id: 'lti', name: 'LTI', kind: 'module', selectable: true, defaultState: 'disabled', configKey: 'artemis.lti.enabled' },
    { id: 'theia', name: 'Theia', kind: 'module', selectable: true, defaultState: 'disabled', configKey: 'artemis.theia.enabled' },
];

const RELATION_SPECS: RelationSpec[] = [
    { parentId: 'artemis', childId: 'teaching-and-content', relationType: 'group', groupType: 'and', order: 1 },
    { parentId: 'teaching-and-content', childId: 'lecture', relationType: 'optional', order: 1 },
    { parentId: 'teaching-and-content', childId: 'tutorialgroup', relationType: 'optional', order: 2 },
    { parentId: 'teaching-and-content', childId: 'course-workflow', relationType: 'mandatory', order: 3 },
    { parentId: 'teaching-and-content', childId: 'communication', relationType: 'mandatory', order: 4 },
    { parentId: 'artemis', childId: 'exercise-system', relationType: 'group', groupType: 'and', order: 2 },
    { parentId: 'exercise-system', childId: 'exercise-common', relationType: 'mandatory', order: 1 },
    { parentId: 'exercise-system', childId: 'programming', relationType: 'mandatory', order: 2 },
    { parentId: 'exercise-system', childId: 'quiz', relationType: 'mandatory', order: 3 },
    { parentId: 'exercise-system', childId: 'text', relationType: 'optional', order: 4 },
    { parentId: 'exercise-system', childId: 'modeling', relationType: 'optional', order: 5 },
    { parentId: 'exercise-system', childId: 'file-upload', relationType: 'optional', order: 6 },
    { parentId: 'artemis', childId: 'assessment-and-integrity', relationType: 'group', groupType: 'and', order: 3 },
    { parentId: 'assessment-and-integrity', childId: 'exam', relationType: 'optional', order: 1 },
    { parentId: 'assessment-and-integrity', childId: 'plagiarism', relationType: 'optional', order: 2 },
    { parentId: 'assessment-and-integrity', childId: 'athena', relationType: 'optional', order: 3 },
    { parentId: 'artemis', childId: 'adaptive-learning-and-ai', relationType: 'group', groupType: 'and', order: 4 },
    { parentId: 'adaptive-learning-and-ai', childId: 'atlas', relationType: 'optional', order: 1 },
    { parentId: 'adaptive-learning-and-ai', childId: 'iris', relationType: 'optional', order: 2 },
    { parentId: 'adaptive-learning-and-ai', childId: 'hyperion', relationType: 'optional', order: 3 },
    { parentId: 'artemis', childId: 'platform-integrations', relationType: 'group', groupType: 'and', order: 5 },
    { parentId: 'platform-integrations', childId: 'lti', relationType: 'optional', order: 1 },
    { parentId: 'platform-integrations', childId: 'theia', relationType: 'optional', order: 2 },
];

export function buildMvpFeatureModelResponse(overrides: Partial<FeatureModelResponse> = {}): FeatureModelResponse {
    const features = FEATURE_SPECS.map((spec) => toFeature(spec));
    const relations: Relation[] = RELATION_SPECS.map((spec) => ({
        parentId: spec.parentId,
        childId: spec.childId,
        relationType: spec.relationType,
        groupType: spec.groupType ?? null,
        order: spec.order,
    }));
    const tree = buildTree(features, relations);
    const defaultSelectedFeatureIds = features
        .filter((feature) => feature.selectable && feature.defaultState === 'enabled')
        .map((feature) => feature.id);

    return {
        model: { id: 'artemis-functional-feature-tree', name: 'Artemis Functional Feature Tree', version: '0.1.0' },
        features,
        relations,
        constraints: [],
        tree,
        defaultSelectedFeatureIds,
        warnings: [],
        ...overrides,
    };
}

function toFeature(spec: FeatureSpec): Feature {
    const source = buildSource(spec);
    return {
        id: spec.id,
        name: spec.name,
        kind: spec.kind,
        selectable: spec.selectable,
        description: spec.description ?? null,
        defaultState: spec.defaultState,
        source,
    };
}

function buildSource(spec: FeatureSpec): FeatureSource | null {
    const hasSourceData =
        spec.configKey !== undefined ||
        spec.springProfile !== undefined ||
        spec.frontendConstant !== undefined ||
        spec.backendConditionClass !== undefined ||
        (spec.evidence !== undefined && spec.evidence.length > 0);
    if (!hasSourceData) {
        return null;
    }
    return {
        configKey: spec.configKey ?? null,
        springProfile: spec.springProfile ?? null,
        frontendConstant: spec.frontendConstant ?? null,
        backendConditionClass: spec.backendConditionClass ?? null,
        evidence: spec.evidence ?? [],
    };
}

function buildTree(features: Feature[], relations: Relation[]): FeatureTreeNode {
    const featuresById = new Map<string, Feature>(features.map((feature) => [feature.id, feature]));
    const childrenByParent = new Map<string, Relation[]>();
    for (const relation of relations) {
        const list = childrenByParent.get(relation.parentId) ?? [];
        list.push(relation);
        childrenByParent.set(relation.parentId, list);
    }
    for (const list of childrenByParent.values()) {
        list.sort((left, right) => left.order - right.order);
    }
    const rootFeature = features.find((feature) => feature.kind === 'root');
    if (!rootFeature) {
        throw new Error('Test fixture is missing a root feature.');
    }
    return buildSubtree(rootFeature, null, featuresById, childrenByParent);
}

function buildSubtree(
    feature: Feature,
    incomingRelation: IncomingRelation | null,
    featuresById: Map<string, Feature>,
    childrenByParent: Map<string, Relation[]>,
): FeatureTreeNode {
    const childRelations = childrenByParent.get(feature.id) ?? [];
    const children: FeatureTreeNode[] = childRelations.map((relation) => {
        const childFeature = featuresById.get(relation.childId);
        if (!childFeature) {
            throw new Error(`Test fixture references missing child id ${relation.childId}.`);
        }
        const incoming: IncomingRelation = {
            parentId: relation.parentId,
            childId: relation.childId,
            relationType: relation.relationType,
            groupType: relation.groupType,
            order: relation.order,
        };
        return buildSubtree(childFeature, incoming, featuresById, childrenByParent);
    });
    return { feature, incomingRelation, children };
}
