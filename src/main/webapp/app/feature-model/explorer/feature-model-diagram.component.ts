import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { HierarchyPointLink, HierarchyPointNode, hierarchy as d3Hierarchy, tree as d3Tree } from 'd3-hierarchy';

import { Feature, FeatureTreeNode, IncomingRelation, RelationType } from '../core/feature-model.types';

interface DiagramTreeData {
    feature: Feature;
    incomingRelation: IncomingRelation | null;
    children: DiagramTreeData[];
    hiddenDescendantCount: number;
    hasChildrenInSource: boolean;
}

interface DiagramNode {
    id: string;
    name: string;
    displayName: string;
    kind: string;
    x: number;
    y: number;
    transform: string;
    incomingRelationType: RelationType | null;
    markerCx: number;
    markerCy: number;
    hasChildren: boolean;
    isCollapsed: boolean;
    hiddenDescendantCount: number;
    toggleLabel: string;
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
const NODE_HEIGHT = 34;
const SIBLING_SPACING = 12;
const DEPTH_SPACING = 56;
const MARKER_OFFSET = 8;
const NODE_RADIUS = 4;
const TOGGLE_BADGE_WIDTH = 28;
const TOGGLE_BADGE_HEIGHT = 18;
const MAX_NAME_LENGTH = 18;
const HORIZONTAL_PADDING = NODE_WIDTH / 2 + TOGGLE_BADGE_WIDTH;
const VERTICAL_PADDING = NODE_HEIGHT;

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
    readonly expandedIds = input.required<ReadonlySet<string>>();
    readonly selectedId = input<string | undefined>(undefined);

    readonly selectFeature = output<string>();
    readonly toggleExpand = output<string>();

    readonly nodeWidth = NODE_WIDTH;
    readonly nodeHeight = NODE_HEIGHT;
    readonly nodeHalfWidth = NODE_WIDTH / 2;
    readonly nodeHalfHeight = NODE_HEIGHT / 2;
    readonly nodeRadius = NODE_RADIUS;
    readonly toggleBadgeWidth = TOGGLE_BADGE_WIDTH;
    readonly toggleBadgeHeight = TOGGLE_BADGE_HEIGHT;
    readonly toggleBadgeHalfWidth = TOGGLE_BADGE_WIDTH / 2;
    readonly toggleBadgeHalfHeight = TOGGLE_BADGE_HEIGHT / 2;
    readonly toggleBadgeOffsetX = NODE_WIDTH / 2;
    readonly toggleTransform = `translate(${NODE_WIDTH / 2}, 0)`;

    private readonly augmentedTree = computed(() => buildDiagramTree(this.tree(), this.expandedIds()));

    private readonly layout = computed(() => {
        const root = d3Hierarchy<DiagramTreeData>(this.augmentedTree(), (node) => node.children);
        const treeLayout = d3Tree<DiagramTreeData>()
            .nodeSize([NODE_HEIGHT + SIBLING_SPACING, NODE_WIDTH + DEPTH_SPACING])
            .separation((a, b) => (a.parent === b.parent ? 1 : 1.2));
        return treeLayout(root);
    });

    readonly nodes = computed<DiagramNode[]>(() => {
        const descendants = this.layout().descendants();
        const selectedId = this.selectedId();
        const matched = this.matchedIds();
        return descendants.map((node) => decorateNode(node, selectedId, matched));
    });

    readonly links = computed<DiagramLink[]>(() =>
        this.layout()
            .links()
            .map((link) => ({
                id: link.target.data.feature.id,
                path: linkPath(link),
            })),
    );

    private readonly bounds = computed(() => {
        const decorated = this.nodes();
        if (decorated.length === 0) {
            return { minX: 0, maxX: 0, minY: 0, maxY: 0 };
        }
        let minX = Number.POSITIVE_INFINITY;
        let maxX = Number.NEGATIVE_INFINITY;
        let minY = Number.POSITIVE_INFINITY;
        let maxY = Number.NEGATIVE_INFINITY;
        for (const node of decorated) {
            if (node.x < minX) minX = node.x;
            if (node.x > maxX) maxX = node.x;
            if (node.y < minY) minY = node.y;
            if (node.y > maxY) maxY = node.y;
        }
        return { minX, maxX, minY, maxY };
    });

    readonly width = computed(() => {
        const { minX, maxX } = this.bounds();
        return maxX - minX + HORIZONTAL_PADDING * 2;
    });

    readonly height = computed(() => {
        const { minY, maxY } = this.bounds();
        return maxY - minY + VERTICAL_PADDING * 2;
    });

    readonly viewBox = computed(() => {
        const { minX, minY } = this.bounds();
        return `${minX - HORIZONTAL_PADDING} ${minY - VERTICAL_PADDING} ${this.width()} ${this.height()}`;
    });

    onSelect(id: string): void {
        this.selectFeature.emit(id);
    }

    onToggle(event: Event, id: string): void {
        event.stopPropagation();
        this.toggleExpand.emit(id);
    }
}

function buildDiagramTree(node: FeatureTreeNode, expandedIds: ReadonlySet<string>): DiagramTreeData {
    const hasChildrenInSource = node.children.length > 0;
    const isExpanded = !hasChildrenInSource || expandedIds.has(node.feature.id);
    if (!isExpanded) {
        return {
            feature: node.feature,
            incomingRelation: node.incomingRelation,
            children: [],
            hiddenDescendantCount: countDescendants(node),
            hasChildrenInSource,
        };
    }
    return {
        feature: node.feature,
        incomingRelation: node.incomingRelation,
        children: node.children.map((child) => buildDiagramTree(child, expandedIds)),
        hiddenDescendantCount: 0,
        hasChildrenInSource,
    };
}

function countDescendants(node: FeatureTreeNode): number {
    let count = node.children.length;
    for (const child of node.children) {
        count += countDescendants(child);
    }
    return count;
}

function decorateNode(
    node: HierarchyPointNode<DiagramTreeData>,
    selectedId: string | undefined,
    matched: ReadonlySet<string>,
): DiagramNode {
    const data = node.data;
    const feature = data.feature;
    const incoming = data.incomingRelation;
    const svgX = node.y;
    const svgY = node.x;
    const hasChildren = data.hasChildrenInSource;
    const isCollapsed = hasChildren && data.children.length === 0;
    const toggleLabel = isCollapsed ? `+${data.hiddenDescendantCount}` : '−';
    return {
        id: feature.id,
        name: feature.name,
        displayName: truncate(feature.name, MAX_NAME_LENGTH),
        kind: feature.kind,
        x: svgX,
        y: svgY,
        transform: `translate(${svgX}, ${svgY})`,
        incomingRelationType: incoming?.relationType ?? null,
        markerCx: svgX - NODE_WIDTH / 2 - MARKER_OFFSET,
        markerCy: svgY,
        hasChildren,
        isCollapsed,
        hiddenDescendantCount: data.hiddenDescendantCount,
        toggleLabel,
        isSelected: feature.id === selectedId,
        isMatched: matched.has(feature.id),
        isStructural: !feature.selectable,
        isRoot: feature.kind === 'root',
    };
}

function linkPath(link: HierarchyPointLink<DiagramTreeData>): string {
    const sx = link.source.y + NODE_WIDTH / 2;
    const sy = link.source.x;
    const tx = link.target.y - NODE_WIDTH / 2;
    const ty = link.target.x;
    return `M ${sx} ${sy} L ${tx} ${ty}`;
}

function truncate(value: string, limit: number): string {
    if (value.length <= limit) {
        return value;
    }
    return `${value.slice(0, limit - 1).trimEnd()}…`;
}
