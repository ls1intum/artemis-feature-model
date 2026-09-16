import { describe, expect, it } from 'vitest';

import {
    collectAllNodeIds,
    collectAncestorIds,
    countSubFeatures,
    featureKindDotClass,
    featureKindLabel,
    formatFeatureCategory,
    formatFeatureKind,
    collectExpandableNodeIds,
    countTreeNodes,
    filterTreeByQuery,
    findNodeById,
    usageArea,
    usageFeature,
    withoutSubFeatures,
} from './feature-model-tree.utils';
import { Feature, FeatureTreeNode, IncomingRelation, RelationType } from './feature-model.types';

function makeFeature(id: string, name: string): Feature {
    return {
        id,
        name,
        kind: 'module',
        selectable: true,
        description: null,
        defaultState: 'enabled',
        source: null,
        category: 'functional',
        visibleTo: [],
        configurableBy: ['teacher', 'maintainer'],
        requiresCapabilities: [],
        artifactMappings: [],
        extraction: null,
    };
}

function makeIncoming(parentId: string, childId: string, relationType: RelationType): IncomingRelation {
    return { parentId, childId, relationType, groupType: null, order: 1 };
}

/** A FeatureUsage sub-feature per contract C-1; the id is deliberately unrelated to the label. */
function makeSubFeature(ownerId: string, id: string, name: string, label: string): FeatureTreeNode {
    return {
        feature: {
            ...makeFeature(id, name),
            kind: 'sub-feature',
            selectable: false,
            category: 'derived',
            defaultState: 'not_applicable',
            configurableBy: [],
            source: { configKey: null, springProfile: null, clientConstant: null, serverConditionClass: 'LectureEnabled', usageLabel: label, evidence: ['LectureResource.java:88'] },
        },
        incomingRelation: makeIncoming(ownerId, id, 'mandatory'),
        children: [],
    };
}

/** The sample tree with two sub-features below lecture and one below programming. */
function buildTreeWithSubFeatures(): FeatureTreeNode {
    const root = buildSampleTree();
    const lecture = findNodeById(root, 'lecture');
    const programming = findNodeById(root, 'programming');
    lecture?.children.push(makeSubFeature('lecture', 'sub-1', 'Transcription', 'ai/transcription'), makeSubFeature('lecture', 'sub-2', 'Lectures', 'authoring/lectures'));
    programming?.children.push(makeSubFeature('programming', 'sub-3', 'Repositories', 'vcs/repositories'));
    return root;
}

function buildSampleTree(): FeatureTreeNode {
    const lecture: FeatureTreeNode = {
        feature: { ...makeFeature('lecture', 'Lecture'), kind: 'module' },
        incomingRelation: makeIncoming('teaching-and-content', 'lecture', 'optional'),
        children: [],
    };
    const programming: FeatureTreeNode = {
        feature: { ...makeFeature('programming', 'Programming'), kind: 'module' },
        incomingRelation: makeIncoming('exercise-system', 'programming', 'mandatory'),
        children: [],
    };
    const teaching: FeatureTreeNode = {
        feature: { ...makeFeature('teaching-and-content', 'Teaching and Content'), kind: 'group', selectable: false },
        incomingRelation: makeIncoming('artemis', 'teaching-and-content', 'group'),
        children: [lecture],
    };
    const exercise: FeatureTreeNode = {
        feature: { ...makeFeature('exercise-system', 'Exercise System'), kind: 'group', selectable: false },
        incomingRelation: makeIncoming('artemis', 'exercise-system', 'group'),
        children: [programming],
    };
    return {
        feature: { ...makeFeature('artemis', 'Artemis'), kind: 'root', selectable: false },
        incomingRelation: null,
        children: [teaching, exercise],
    };
}

describe('feature-model-tree.utils', () => {
    it('counts all nodes in a tree', () => {
        expect(countTreeNodes(buildSampleTree())).toBe(5);
    });

    it('returns zero when counting a null tree', () => {
        expect(countTreeNodes(null)).toBe(0);
    });

    it('finds a node by id', () => {
        const node = findNodeById(buildSampleTree(), 'lecture');
        expect(node?.feature.name).toBe('Lecture');
    });

    it('returns null when an id is missing', () => {
        expect(findNodeById(buildSampleTree(), 'missing')).toBeNull();
    });

    it('collects only the ids of nodes that have children', () => {
        expect(collectExpandableNodeIds(buildSampleTree())).toEqual([
            'artemis',
            'teaching-and-content',
            'exercise-system',
        ]);
    });

    it('collects every node id in pre-order', () => {
        expect(collectAllNodeIds(buildSampleTree())).toEqual([
            'artemis',
            'teaching-and-content',
            'lecture',
            'exercise-system',
            'programming',
        ]);
    });

    it('returns the original tree for an empty query', () => {
        const root = buildSampleTree();
        const result = filterTreeByQuery(root, '   ');
        expect(result.tree).toBe(root);
        expect(result.matchedIds.size).toBe(0);
    });

    it('filters by feature name preserving ancestors', () => {
        const result = filterTreeByQuery(buildSampleTree(), 'Lecture');
        expect(result.tree).not.toBeNull();
        expect(countTreeNodes(result.tree)).toBe(3);
        expect(result.matchedIds.has('lecture')).toBe(true);
        expect(result.ancestorIds.has('artemis')).toBe(true);
        expect(result.ancestorIds.has('teaching-and-content')).toBe(true);
    });

    it('filters by feature id case-insensitively', () => {
        const result = filterTreeByQuery(buildSampleTree(), 'PROGRAMMING');
        expect(result.tree).not.toBeNull();
        expect(result.matchedIds.has('programming')).toBe(true);
        const exerciseBranch = result.tree?.children.find((child) => child.feature.id === 'exercise-system');
        expect(exerciseBranch?.children).toHaveLength(1);
        const teachingBranch = result.tree?.children.find((child) => child.feature.id === 'teaching-and-content');
        expect(teachingBranch).toBeUndefined();
    });

    it('returns an empty tree when nothing matches', () => {
        const result = filterTreeByQuery(buildSampleTree(), 'nonsense');
        expect(result.tree).toBeNull();
        expect(result.matchedIds.size).toBe(0);
    });

    it('collects ancestor ids for a single target deep in the tree', () => {
        const result = collectAncestorIds(buildSampleTree(), new Set(['programming']));
        expect(result.size).toBe(2);
        expect(result.has('artemis')).toBe(true);
        expect(result.has('exercise-system')).toBe(true);
        expect(result.has('programming')).toBe(false);
    });

    it('collects ancestors across multiple targets without duplicates', () => {
        const result = collectAncestorIds(buildSampleTree(), new Set(['programming', 'lecture']));
        expect(result.has('artemis')).toBe(true);
        expect(result.has('exercise-system')).toBe(true);
        expect(result.has('teaching-and-content')).toBe(true);
    });

    it('returns an empty set when targets are missing or the tree is null', () => {
        expect(collectAncestorIds(null, new Set(['programming'])).size).toBe(0);
        expect(collectAncestorIds(buildSampleTree(), new Set<string>()).size).toBe(0);
        expect(collectAncestorIds(buildSampleTree(), new Set(['nonsense'])).size).toBe(0);
    });
});

describe('sub-feature helpers', () => {
    it('excludes sub-features from the node count and counts them separately', () => {
        const root = buildTreeWithSubFeatures();
        expect(countTreeNodes(root)).toBe(5);
        expect(countSubFeatures(root)).toBe(3);
        expect(countSubFeatures(buildSampleTree())).toBe(0);
        expect(countSubFeatures(null)).toBe(0);
    });

    it('prunes sub-feature nodes and keeps the original reference when there are none', () => {
        const plain = buildSampleTree();
        expect(withoutSubFeatures(plain)).toBe(plain);
        expect(withoutSubFeatures(null)).toBeNull();

        const pruned = withoutSubFeatures(buildTreeWithSubFeatures());
        expect(collectAllNodeIds(pruned)).toEqual(['artemis', 'teaching-and-content', 'lecture', 'exercise-system', 'programming']);
        expect(countSubFeatures(pruned)).toBe(0);
    });

    it('splits a usage label into area and feature at the first slash', () => {
        expect(usageArea('authoring/lectures')).toBe('authoring');
        expect(usageFeature('authoring/lectures')).toBe('lectures');
        expect(usageArea('units/attachment-video-units')).toBe('units');
        expect(usageFeature('units/attachment-video-units')).toBe('attachment-video-units');
        expect(usageArea('plain')).toBe('plain');
        expect(usageFeature('plain')).toBe('plain');
    });

    it('matches the search query against the usage label', () => {
        const result = filterTreeByQuery(buildTreeWithSubFeatures(), 'AUTHORING/');
        expect(result.matchedIds.has('sub-2')).toBe(true);
        expect(result.matchedIds.has('sub-1')).toBe(false);
        expect(result.ancestorIds.has('lecture')).toBe(true);
        expect(countSubFeatures(result.tree)).toBe(1);
    });

    it('labels and colours the sub-feature kind', () => {
        expect(formatFeatureKind('sub-feature')).toBe('Sub-feature');
        expect(featureKindLabel('sub-feature', 'derived')).toBe('Sub-feature');
        expect(featureKindDotClass('sub-feature')).toBe('fm-kind-dot--sub-feature');
    });

    it('still lists owners with only sub-feature children as expandable', () => {
        expect(collectExpandableNodeIds(buildTreeWithSubFeatures())).toEqual(['artemis', 'teaching-and-content', 'lecture', 'exercise-system', 'programming']);
    });
});

describe('featureKindLabel', () => {
    it('labels the technical leaves Infrastructure so "Feature" does not imply the modules are not features', () => {
        expect(featureKindLabel('feature', 'technical')).toBe('Infrastructure');
        expect(featureKindLabel('module', 'functional')).toBe('Module');
        expect(featureKindLabel('group', 'technical')).toBe('Group');
        expect(featureKindLabel('root', 'derived')).toBe('Root');
    });

    it('keys the label on the category, so a functional node of kind feature stays a Feature', () => {
        expect(featureKindLabel('feature', 'functional')).toBe('Feature');
    });

    it('passes unknown kinds through', () => {
        expect(featureKindLabel('experimental', 'functional')).toBe('experimental');
    });
});

describe('formatFeatureCategory', () => {
    it('capitalises the known categories and passes anything else through', () => {
        expect(formatFeatureCategory('technical')).toBe('Technical');
        expect(formatFeatureCategory('functional')).toBe('Functional');
        expect(formatFeatureCategory('derived')).toBe('Derived');
        expect(formatFeatureCategory('capability')).toBe('Capability');
        expect(formatFeatureCategory('speculative')).toBe('speculative');
    });
});
