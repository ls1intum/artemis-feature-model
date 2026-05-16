import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { HierarchyPointLink, HierarchyPointNode, hierarchy as d3Hierarchy, tree as d3Tree } from 'd3-hierarchy';

import { FeatureTreeNode, RelationType } from '../core/feature-model.types';

interface DiagramNode {
    id: string;
    name: string;
    displayName: string;
    kind: string;
    x: number;
    y: number;
    transform: string;
    incomingRelationType: RelationType | null;
    relationMarkerY: number;
    isSelected: boolean;
    isMatched: boolean;
    isStructural: boolean;
    isRoot: boolean;
}

interface DiagramLink {
    id: string;
    path: string;
}

const NODE_WIDTH = 132;
const NODE_HEIGHT = 36;
const NODE_X_SPACING = 24;
const NODE_Y_SPACING = 84;
const NODE_RADIUS = 4;
const MARKER_OFFSET = 8;
const MAX_NAME_LENGTH = 18;
const VIEWPORT_PADDING = 32;

@Component({
    selector: 'fm-feature-model-diagram',
    standalone: true,
    imports: [],
    changeDetection: ChangeDetectionStrategy.OnPush,
    templateUrl: './feature-model-diagram.component.html',
    styleUrl: './feature-model-diagram.component.scss',
})
export class FeatureModelDiagramComponent {
    readonly tree = input.required<FeatureTreeNode>();
    readonly matchedIds = input.required<ReadonlySet<string>>();
    readonly selectedId = input<string | undefined>(undefined);

    readonly selectFeature = output<string>();

    readonly nodeWidth = NODE_WIDTH;
    readonly nodeHeight = NODE_HEIGHT;
    readonly nodeHalfWidth = NODE_WIDTH / 2;
    readonly nodeHalfHeight = NODE_HEIGHT / 2;
    readonly nodeRadius = NODE_RADIUS;

    private readonly layout = computed(() => {
        const root = d3Hierarchy<FeatureTreeNode>(this.tree(), (node) => node.children);
        const treeLayout = d3Tree<FeatureTreeNode>()
            .nodeSize([NODE_WIDTH + NODE_X_SPACING, NODE_HEIGHT + NODE_Y_SPACING])
            .separation((a, b) => (a.parent === b.parent ? 1 : 1.2));
        return treeLayout(root);
    });

    private readonly bounds = computed(() => {
        const descendants = this.layout().descendants();
        if (descendants.length === 0) {
            return { minX: 0, maxX: 0, minY: 0, maxY: 0 };
        }
        let minX = Number.POSITIVE_INFINITY;
        let maxX = Number.NEGATIVE_INFINITY;
        let minY = Number.POSITIVE_INFINITY;
        let maxY = Number.NEGATIVE_INFINITY;
        for (const node of descendants) {
            if (node.x < minX) minX = node.x;
            if (node.x > maxX) maxX = node.x;
            if (node.y < minY) minY = node.y;
            if (node.y > maxY) maxY = node.y;
        }
        return { minX, maxX, minY, maxY };
    });

    readonly width = computed(() => {
        const { minX, maxX } = this.bounds();
        return maxX - minX + NODE_WIDTH + VIEWPORT_PADDING * 2;
    });

    readonly height = computed(() => {
        const { minY, maxY } = this.bounds();
        return maxY - minY + NODE_HEIGHT + VIEWPORT_PADDING * 2;
    });

    readonly viewBox = computed(() => {
        const { minX, minY } = this.bounds();
        const x = minX - NODE_WIDTH / 2 - VIEWPORT_PADDING;
        const y = minY - NODE_HEIGHT / 2 - VIEWPORT_PADDING;
        return `${x} ${y} ${this.width()} ${this.height()}`;
    });

    readonly nodes = computed<DiagramNode[]>(() => {
        const descendants = this.layout().descendants();
        const selectedId = this.selectedId();
        const matched = this.matchedIds();
        return descendants.map((node) => decorateNode(node, selectedId, matched));
    });

    readonly links = computed<DiagramLink[]>(() => {
        return this.layout().links().map((link) => ({
            id: link.target.data.feature.id,
            path: linkPath(link),
        }));
    });

    onSelect(id: string): void {
        this.selectFeature.emit(id);
    }
}

function decorateNode(
    node: HierarchyPointNode<FeatureTreeNode>,
    selectedId: string | undefined,
    matched: ReadonlySet<string>,
): DiagramNode {
    const feature = node.data.feature;
    const incoming = node.data.incomingRelation;
    return {
        id: feature.id,
        name: feature.name,
        displayName: truncate(feature.name, MAX_NAME_LENGTH),
        kind: feature.kind,
        x: node.x,
        y: node.y,
        transform: `translate(${node.x}, ${node.y})`,
        incomingRelationType: incoming?.relationType ?? null,
        relationMarkerY: -NODE_HEIGHT / 2 - MARKER_OFFSET,
        isSelected: feature.id === selectedId,
        isMatched: matched.has(feature.id),
        isStructural: !feature.selectable,
        isRoot: feature.kind === 'root',
    };
}

function linkPath(link: HierarchyPointLink<FeatureTreeNode>): string {
    const sx = link.source.x;
    const sy = link.source.y + NODE_HEIGHT / 2;
    const tx = link.target.x;
    const ty = link.target.y - NODE_HEIGHT / 2;
    return `M ${sx} ${sy} L ${tx} ${ty}`;
}

function truncate(value: string, limit: number): string {
    if (value.length <= limit) {
        return value;
    }
    return `${value.slice(0, limit - 1).trimEnd()}…`;
}
