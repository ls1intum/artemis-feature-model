import { describe, expect, it } from 'vitest';

import {
    collectAllNodeIds,
    collectExpandableNodeIds,
    countTreeNodes,
    filterTreeByQuery,
    findNodeById,
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
    };
}

function makeIncoming(parentId: string, childId: string, relationType: RelationType): IncomingRelation {
    return { parentId, childId, relationType, groupType: null, order: 1 };
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
});
