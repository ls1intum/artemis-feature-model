import { DeploymentProfileSummary, FeatureAvailability, OptionAvailability, WorkflowAvailability } from './deployment-profile.types';
import {
    DefaultState,
    Feature,
    FeatureCategory,
    FeatureKind,
    FeatureModelResponse,
    FeatureSource,
    FeatureTreeNode,
    GroupType,
    IncomingRelation,
    Relation,
    RelationType,
} from './feature-model.types';
import { GuidedDecisionOption, GuidedWorkflow } from './guided-workflow.types';

interface FeatureSpec {
    id: string;
    name: string;
    kind: FeatureKind;
    selectable: boolean;
    defaultState: DefaultState;
    description?: string;
    category?: FeatureCategory;
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
    { id: 'theia', name: 'EduIDE', kind: 'module', selectable: true, defaultState: 'disabled', configKey: 'artemis.theia.enabled' },
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
        model: {
            id: 'artemis-functional-feature-tree',
            name: 'Artemis Functional Feature Tree',
            version: '0.1.0',
            status: 'published',
            sourceCommitSha: null,
        },
        features,
        relations,
        constraints: [],
        tree,
        defaultSelectedFeatureIds,
        warnings: [],
        ...overrides,
    };
}

export function buildGuidedWorkflowFixture(overrides: Partial<GuidedWorkflow> = {}): GuidedWorkflow {
    const workflow: GuidedWorkflow = {
        workflow: {
            id: 'artemis-guided-configuration',
            name: 'Artemis Guided Configuration Workflow',
            version: '0.1.0',
            featureModelId: 'artemis-functional-feature-tree',
            featureModelVersion: '0.1.0',
            defaultTemplateId: 'custom-configuration',
        },
        useCaseTemplates: [
            {
                id: 'minimal-teaching-setup',
                label: 'Minimal teaching setup',
                description: 'Starts with the common course workflow, communication, and core exercise infrastructure.',
                selectedFeatureIds: ['course-workflow', 'communication', 'exercise-common', 'programming', 'quiz'],
                deselectedFeatureIds: ['athena', 'iris', 'hyperion', 'lti', 'theia'],
                recommendedStepIds: ['teaching-content', 'exercise-types', 'ai-and-integrations'],
                consequences: ['Leaves optional AI and integration features disabled until explicitly selected.'],
                warnings: [],
            },
            {
                id: 'ai-enabled-course',
                label: 'AI-enabled course',
                description: 'Starts with the standard course setup plus AI decisions.',
                selectedFeatureIds: ['course-workflow', 'communication', 'exercise-common', 'programming', 'quiz', 'text', 'atlas', 'iris'],
                deselectedFeatureIds: ['lti'],
                recommendedStepIds: ['exercise-types', 'ai-and-integrations'],
                consequences: ['Introduces external service and secret-reference requirements for later deployment profile validation.'],
                warnings: ['AI options are only usable when the selected deployment profile provides the required services.'],
            },
            {
                id: 'custom-configuration',
                label: 'Custom configuration',
                description: 'Starts from the feature model defaults and lets the user review every guided decision.',
                selectedFeatureIds: [],
                deselectedFeatureIds: [],
                recommendedStepIds: ['teaching-content', 'exercise-types', 'ai-and-integrations'],
                consequences: ['Uses the current model defaults as the initial selection.'],
                warnings: [],
            },
        ],
        steps: [
            {
                id: 'teaching-content',
                title: 'Teaching Content',
                order: 1,
                description: 'Configure lecture material, tutorial group, course workflow, and communication features.',
                decisions: [
                    {
                        id: 'teaching-content-baseline',
                        question: 'Which teaching content capabilities should be available?',
                        description: 'Select the course-facing content and communication features that teachers normally use.',
                        selectionMode: 'multiple',
                        options: [
                            {
                                id: 'enable-lecture-materials',
                                label: 'Enable lecture materials',
                                description: 'Allow teachers to manage lectures and lecture units.',
                                selects: ['lecture'],
                                deselects: [],
                                requiresCapabilities: [],
                                artifactImpacts: ['Sets artemis.lecture.enabled = true in the generated external configuration overlay.'],
                                enabledOutcome: ['Instructors can publish lecture material and connect course content with exercises.'],
                                recommendedWhen: ['Your course uses slides, recordings, linked resources, or lecture units alongside exercises.'],
                                thingsToKnow: ['This is useful for material-centered courses even when exercises stay simple.'],
                                warnings: [],
                            },
                        ],
                    },
                ],
            },
            {
                id: 'exercise-types',
                title: 'Exercise Types',
                order: 2,
                description: 'Choose the exercise types that should be available in the course setup.',
                decisions: [
                    {
                        id: 'exercise-type-selection',
                        question: 'Which exercise types should teachers be able to use?',
                        description: 'Exercise choices map directly to functional feature ids.',
                        selectionMode: 'multiple',
                        options: [
                            {
                                id: 'enable-programming-and-quiz',
                                label: 'Programming and quiz exercises',
                                description: 'Keep the default programming and quiz exercise baseline enabled.',
                                selects: ['exercise-common', 'programming', 'quiz'],
                                deselects: [],
                                requiresCapabilities: [],
                                artifactImpacts: [],
                                enabledOutcome: ['Instructors can run programming exercises with individual feedback and quiz exercises.'],
                                recommendedWhen: ['Your course includes code-based assignments, automatically assessed tasks, or quizzes.'],
                                thingsToKnow: ['Programming exercises usually require version control and continuous integration.'],
                                warnings: ['Programming exercises still need CI and VCS capabilities in the deployment profile.'],
                            },
                            {
                                id: 'enable-written-exercise-types',
                                label: 'Written exercise types',
                                description: 'Enable text, modeling, and file upload exercises.',
                                selects: ['text', 'modeling', 'file-upload'],
                                deselects: [],
                                requiresCapabilities: [],
                                artifactImpacts: ['Sets artemis.text.enabled = true in the generated external configuration overlay.'],
                                enabledOutcome: ['Instructors can use text, modeling, and file upload exercises.'],
                                recommendedWhen: ['Your course needs essays, explanations, diagrams, PDFs, images, or other file submissions.'],
                                thingsToKnow: ['Assessment may rely more on tutors and grading criteria than fully automatic tests.'],
                                warnings: [],
                            },
                        ],
                    },
                ],
            },
            {
                id: 'ai-and-integrations',
                title: 'AI and Integrations',
                order: 3,
                description: 'Configure adaptive learning, AI tutoring, LTI, and online IDE integration features.',
                decisions: [
                    {
                        id: 'ai-feature-selection',
                        question: 'Which AI and adaptive learning features should be enabled?',
                        description: 'AI features explain both functional consequences and later deployment profile requirements.',
                        selectionMode: 'multiple',
                        options: [
                            {
                                id: 'enable-iris',
                                label: 'Enable Iris AI Tutor',
                                description: 'Enable AI tutoring support through Iris.',
                                selects: ['iris'],
                                deselects: [],
                                requiresCapabilities: ['pyris-service', 'pyris-secret'],
                                artifactImpacts: ['Sets artemis.iris.enabled = true in the generated external configuration overlay.'],
                                enabledOutcome: ['Students and instructors can receive AI tutoring support.'],
                                recommendedWhen: ['Your course wants AI-assisted help for recurring questions or learning progress.'],
                                thingsToKnow: ['Iris requires administrator setup before users can benefit from it.'],
                                warnings: ['Only available when the active deployment profile provides AI tutoring support.'],
                            },
                        ],
                    },
                ],
            },
        ],
        finalReviewGroups: [
            { id: 'teaching-and-content', groupNodeId: 'teaching-and-content', title: 'Teaching Content', order: 1, featureIds: ['lecture', 'course-workflow', 'communication'] },
            {
                id: 'exercise-system',
                groupNodeId: 'exercise-system',
                title: 'Exercise Types',
                order: 2,
                featureIds: ['exercise-common', 'programming', 'quiz', 'text', 'modeling', 'file-upload'],
            },
            { id: 'adaptive-learning-and-ai', groupNodeId: 'adaptive-learning-and-ai', title: 'AI and Adaptive Learning', order: 3, featureIds: ['iris'] },
        ],
    };
    return { ...workflow, ...overrides };
}

const DEFAULT_ARTEMIS_PROFILE_SUMMARY: DeploymentProfileSummary = {
    id: 'default-artemis-profile',
    name: 'Default Artemis Deployment Context',
    version: '1.0.0',
    status: 'published',
    defaultProfile: true,
};

/**
 * Builds profile-aware availability derived from the model and guided workflow fixtures, mirroring the backend
 * CapabilityResolutionService and the single bundled deployment context. By default every referenced capability is
 * provided, so all guided options are available. Pass `providedCapabilities` to simulate a maintainer local override
 * that restricts capabilities (used to exercise the latent gating and advanced debug surfaces).
 */
export function buildWorkflowAvailabilityFixture(options: { providedCapabilities?: string[] } = {}): WorkflowAvailability {
    const allOptions = buildGuidedWorkflowFixture().steps.flatMap((step) => step.decisions.flatMap((decision) => decision.options));
    const allReferencedCapabilities = [...new Set(allOptions.flatMap((option) => option.requiresCapabilities))];
    const provided = options.providedCapabilities ?? allReferencedCapabilities;

    const optionAvailability: OptionAvailability[] = allOptions.map((option) => availabilityForOption(option, provided));
    const requiredByFeature = aggregateRequiredCapabilitiesByFeature(allOptions);
    const featureAvailability: FeatureAvailability[] = buildMvpFeatureModelResponse().features.map((feature) =>
        availabilityForFeature(feature, requiredByFeature.get(feature.id) ?? [], provided),
    );

    return {
        activeProfile: DEFAULT_ARTEMIS_PROFILE_SUMMARY,
        availableProfiles: [DEFAULT_ARTEMIS_PROFILE_SUMMARY],
        options: optionAvailability,
        features: featureAvailability,
    };
}

function availabilityForOption(option: GuidedDecisionOption, provided: string[]): OptionAvailability {
    const missing = option.requiresCapabilities.filter((capability) => !provided.includes(capability));
    const available = missing.length === 0;
    return {
        optionId: option.id,
        available,
        requiredCapabilities: [...option.requiresCapabilities],
        missingCapabilities: missing,
        teacherReason: available ? null : 'This option is not available in the current deployment profile.',
    };
}

function aggregateRequiredCapabilitiesByFeature(allOptions: GuidedDecisionOption[]): Map<string, string[]> {
    const requiredByFeature = new Map<string, string[]>();
    for (const option of allOptions) {
        if (option.requiresCapabilities.length === 0) {
            continue;
        }
        for (const featureId of option.selects) {
            const existing = requiredByFeature.get(featureId) ?? [];
            requiredByFeature.set(featureId, [...new Set([...existing, ...option.requiresCapabilities])]);
        }
    }
    return requiredByFeature;
}

function availabilityForFeature(feature: Feature, required: string[], provided: string[]): FeatureAvailability {
    const missing = required.filter((capability) => !provided.includes(capability));
    const available = missing.length === 0;
    return {
        featureId: feature.id,
        featureName: feature.name,
        available,
        profileDependent: required.length > 0,
        requiredCapabilities: required,
        missingCapabilities: missing,
        teacherReason: available ? null : `${feature.name} is not available in the current deployment profile.`,
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
        category: spec.category ?? (spec.selectable ? 'functional' : 'derived'),
        visibleTo: [],
        configurableBy: spec.selectable ? ['teacher', 'maintainer'] : [],
        requiresCapabilities: [],
        artifactMappings: [],
        extraction: {
            method: 'manual-curation',
            confidence: 'high',
            status: 'manually_confirmed',
        },
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
