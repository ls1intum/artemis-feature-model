import { FeatureTreeNode } from './feature-model.types';

export interface TreeFilterResult {
    tree: FeatureTreeNode | null;
    matchedIds: ReadonlySet<string>;
    ancestorIds: ReadonlySet<string>;
}

/**
 * Counts every node in the tree, including the root.
 *
 * @param root Tree root, or `null` when no tree is loaded.
 * @returns Total number of nodes; `0` when `root` is `null`.
 */
export function countTreeNodes(root: FeatureTreeNode | null): number {
    if (!root) {
        return 0;
    }
    let count = 1;
    for (const child of root.children) {
        count += countTreeNodes(child);
    }
    return count;
}

/**
 * Depth-first lookup for the first node whose feature id matches `id`.
 *
 * @param root Tree root, or `null` when no tree is loaded.
 * @param id Feature id to search for.
 * @returns The matching node, or `null` when nothing matches.
 */
export function findNodeById(root: FeatureTreeNode | null, id: string): FeatureTreeNode | null {
    if (!root) {
        return null;
    }
    if (root.feature.id === id) {
        return root;
    }
    for (const child of root.children) {
        const match = findNodeById(child, id);
        if (match) {
            return match;
        }
    }
    return null;
}

/**
 * Returns the ids of nodes that have at least one child, in pre-order. Used by the explorer
 * to populate `userExpandedIds` when the user clicks "Expand all".
 *
 * @param root Tree root, or `null` when no tree is loaded.
 * @returns Pre-order list of expandable node ids; empty when `root` is `null` or every node is a leaf.
 */
export function collectExpandableNodeIds(root: FeatureTreeNode | null): string[] {
    if (!root) {
        return [];
    }
    const ids: string[] = [];
    collectExpandableInto(root, ids);
    return ids;
}

function collectExpandableInto(node: FeatureTreeNode, ids: string[]): void {
    if (node.children.length === 0) {
        return;
    }
    ids.push(node.feature.id);
    for (const child of node.children) {
        collectExpandableInto(child, ids);
    }
}

/**
 * Returns every node id in the tree in pre-order (root first, then each child subtree).
 *
 * @param root Tree root, or `null` when no tree is loaded.
 * @returns Pre-order list of every node id; empty when `root` is `null`.
 */
export function collectAllNodeIds(root: FeatureTreeNode | null): string[] {
    if (!root) {
        return [];
    }
    const ids: string[] = [];
    collectAllInto(root, ids);
    return ids;
}

function collectAllInto(node: FeatureTreeNode, ids: string[]): void {
    ids.push(node.feature.id);
    for (const child of node.children) {
        collectAllInto(child, ids);
    }
}

/**
 * Filters the tree to branches that match `rawQuery` (case-insensitive, trimmed) and keeps the
 * ancestors of each match so the user can see where matches live in the hierarchy. Returns the
 * original tree reference unchanged when the trimmed query is empty.
 *
 * @param root Tree root, or `null` when no tree is loaded.
 * @param rawQuery Raw user search input; whitespace is trimmed and casing is normalized internally.
 * @returns `tree` is the filtered subtree (or the original root on an empty query, or `null` when
 *     no node matches); `matchedIds` are the ids whose own name or id matched; `ancestorIds` are
 *     the ids that were kept only because they have a matching descendant.
 */
export function filterTreeByQuery(root: FeatureTreeNode | null, rawQuery: string): TreeFilterResult {
    if (!root) {
        return { tree: null, matchedIds: new Set(), ancestorIds: new Set() };
    }
    const normalized = rawQuery.trim().toLowerCase();
    if (normalized.length === 0) {
        return { tree: root, matchedIds: new Set(), ancestorIds: new Set() };
    }
    const matchedIds = new Set<string>();
    const ancestorIds = new Set<string>();
    const filtered = filterNode(root, normalized, matchedIds, ancestorIds, []);
    return { tree: filtered, matchedIds, ancestorIds };
}

function filterNode(
    node: FeatureTreeNode,
    needle: string,
    matchedIds: Set<string>,
    ancestorIds: Set<string>,
    parentChain: string[],
): FeatureTreeNode | null {
    const directMatch = matchesNode(node, needle);
    const nextChain = [...parentChain, node.feature.id];
    const keptChildren: FeatureTreeNode[] = [];
    for (const child of node.children) {
        const filteredChild = filterNode(child, needle, matchedIds, ancestorIds, nextChain);
        if (filteredChild) {
            keptChildren.push(filteredChild);
        }
    }
    if (!directMatch && keptChildren.length === 0) {
        return null;
    }
    if (directMatch) {
        matchedIds.add(node.feature.id);
    } else {
        for (const ancestorId of parentChain) {
            ancestorIds.add(ancestorId);
        }
        ancestorIds.add(node.feature.id);
    }
    return {
        feature: node.feature,
        incomingRelation: node.incomingRelation,
        children: keptChildren,
    };
}

function matchesNode(node: FeatureTreeNode, needle: string): boolean {
    const id = node.feature.id.toLowerCase();
    const name = node.feature.name.toLowerCase();
    return id.includes(needle) || name.includes(needle);
}
