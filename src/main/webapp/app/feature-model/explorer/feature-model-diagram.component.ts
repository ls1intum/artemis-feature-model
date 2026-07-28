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
    isConfiguredSelected: boolean;
    isViolation: boolean;
    isWarning: boolean;
    isToggleable: boolean;
}

interface DiagramLink {
    id: string;
    path: string;
}

interface DiagramGroupMarker {
    id: string;
    path: string;
    label: string;
}

interface DiagramPoint {
    x: number;
    y: number;
}

const NODE_WIDTH = 132;
const NODE_HEIGHT = 34;
const SIBLING_SPACING = 12;
const DEPTH_SPACING = 56;
const MARKER_OFFSET = 8;
const GROUP_MARKER_RADIUS = 28;
const NODE_RADIUS = 4;
const TOGGLE_BADGE_WIDTH = 28;
const TOGGLE_BADGE_HEIGHT = 18;
const MAX_NAME_LENGTH = 18;
const HORIZONTAL_PADDING = NODE_WIDTH / 2 + TOGGLE_BADGE_WIDTH;
const VERTICAL_PADDING = NODE_HEIGHT;
const STATUS_INDICATOR_RADIUS = 5;
const STATUS_INDICATOR_OFFSET_X = NODE_WIDTH / 2 - STATUS_INDICATOR_RADIUS - 3;
const STATUS_INDICATOR_OFFSET_Y = -NODE_HEIGHT / 2 + STATUS_INDICATOR_RADIUS + 3;

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
    readonly selectedFeatureIds = input<ReadonlySet<string>>(new Set<string>());
    readonly violationIds = input<ReadonlySet<string>>(new Set<string>());
    readonly warningIds = input<ReadonlySet<string>>(new Set<string>());
    readonly toggleableIds = input<ReadonlySet<string>>(new Set<string>());
    readonly configurationMode = input<boolean>(false);

    readonly selectFeature = output<string>();
    readonly toggleExpand = output<string>();
    readonly toggleSelection = output<string>();

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
    readonly statusIndicatorRadius = STATUS_INDICATOR_RADIUS;
    readonly statusIndicatorOffsetX = STATUS_INDICATOR_OFFSET_X;
    readonly statusIndicatorOffsetY = STATUS_INDICATOR_OFFSET_Y;

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
        const selectedSet = this.selectedFeatureIds();
        const violations = this.violationIds();
        const warnings = this.warningIds();
        const toggleable = this.toggleableIds();
        return descendants.map((node) =>
            decorateNode(node, selectedId, matched, selectedSet, violations, warnings, toggleable),
        );
    });

    readonly links = computed<DiagramLink[]>(() =>
        this.layout()
            .links()
            .map((link) => ({
                id: link.target.data.feature.id,
                path: linkPath(link),
            })),
    );

    readonly alternativeGroupMarkers = computed<DiagramGroupMarker[]>(() => {
        const markers: DiagramGroupMarker[] = [];
        for (const node of this.layout().descendants()) {
            const marker = alternativeGroupMarker(node);
            if (marker) {
                markers.push(marker);
            }
        }
        return markers;
    });

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

    /**
     * Handles a node body click: always emits `selectFeature` so the details panel can focus the
     * clicked feature. In configuration mode, additionally emits `toggleSelection` when the clicked
     * node is in the toggleable set so root/group nodes remain focus-only while selectable modules
     * also flip their selected state.
     *
     * @param node Diagram node descriptor for the clicked feature.
     */
    onSelect(node: DiagramNode): void {
        this.selectFeature.emit(node.id);
        if (this.configurationMode() && node.isToggleable) {
            this.toggleSelection.emit(node.id);
        }
    }

    /**
     * Emits `toggleExpand` for `id` and stops the click from bubbling to the surrounding node
     * `<g>`, which would otherwise also fire `selectFeature` because the badge sits inside the
     * clickable node group.
     *
     * @param event Click or keyboard event from the toggle badge.
     * @param id Feature id whose subtree should be expanded or collapsed.
     */
    onToggle(event: Event, id: string): void {
        event.stopPropagation();
        this.toggleExpand.emit(id);
    }
}

/**
 * Builds the augmented tree that is fed to `d3.hierarchy`. Collapsed subtrees (parents whose id is
 * not in `expandedIds`) become childless leaves carrying a `hiddenDescendantCount`, while expanded
 * subtrees recurse normally. `hasChildrenInSource` is preserved on every node so the renderer can
 * still show a toggle badge on collapsed nodes that had children originally.
 *
 * @param node Source feature-tree node.
 * @param expandedIds Set of ids whose subtrees should remain expanded.
 * @returns Augmented node ready for `d3.hierarchy(...)`.
 */
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

/**
 * Converts a positioned `d3-hierarchy` node into the view-ready `DiagramNode` consumed by the
 * template. Performs two important transformations: (1) the d3 (x, y) pair is swapped so the
 * diagram grows left-to-right (depth becomes the SVG x axis, sibling axis becomes the SVG y
 * axis), and (2) the relation marker's absolute position is precomputed at the left edge of the
 * child box so the template can place markers without doing arithmetic.
 *
 * @param node Positioned node returned by `d3.tree()(root)`.
 * @param selectedId Currently focused feature id, or `undefined` when nothing is focused.
 * @param matched Set of ids highlighted by the current search.
 * @param selectedSet Set of feature ids that are currently configured-selected.
 * @param violations Set of feature ids that have validation violations.
 * @param warnings Set of feature ids that have validation warnings.
 * @param toggleable Set of feature ids that can be toggled in configuration mode.
 * @returns View-ready node with SVG coordinates, marker position, and modifier flags.
 */
function decorateNode(
    node: HierarchyPointNode<DiagramTreeData>,
    selectedId: string | undefined,
    matched: ReadonlySet<string>,
    selectedSet: ReadonlySet<string>,
    violations: ReadonlySet<string>,
    warnings: ReadonlySet<string>,
    toggleable: ReadonlySet<string>,
): DiagramNode {
    const data = node.data;
    const feature = data.feature;
    const svgX = node.y;
    const svgY = node.x;
    const hasChildren = data.hasChildrenInSource;
    const isCollapsed = hasChildren && data.children.length === 0;
    const toggleLabel = isCollapsed ? `+${data.hiddenDescendantCount}` : '−';
    const incomingRelationType = relationMarkerType(node);
    return {
        id: feature.id,
        name: feature.name,
        displayName: truncate(feature.name, MAX_NAME_LENGTH),
        kind: feature.kind,
        x: svgX,
        y: svgY,
        transform: `translate(${svgX}, ${svgY})`,
        incomingRelationType,
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
        isConfiguredSelected: selectedSet.has(feature.id),
        isViolation: violations.has(feature.id),
        isWarning: warnings.has(feature.id),
        isToggleable: toggleable.has(feature.id),
    };
}

function relationMarkerType(node: HierarchyPointNode<DiagramTreeData>): RelationType | null {
    const parentGroupType = node.parent?.data.incomingRelation?.groupType;
    if (parentGroupType === 'alternative') {
        return null;
    }
    return node.data.incomingRelation?.relationType ?? null;
}

/**
 * Builds the SVG path for a parent-to-child link, applying the same LTR axis swap as
 * `decorateNode`: the line starts at the parent's right edge (depth + half-width) and ends at the
 * child's left edge (depth − half-width), with both y coordinates taken from the d3 sibling axis.
 *
 * @param link Source/target pair returned by `positionedRoot.links()`.
 * @returns SVG `path` `d` attribute drawing a single straight line between the two nodes.
 */
function linkPath(link: HierarchyPointLink<DiagramTreeData>): string {
    const sx = link.source.y + NODE_WIDTH / 2;
    const sy = link.source.x;
    const tx = link.target.y - NODE_WIDTH / 2;
    const ty = link.target.x;
    return `M ${sx} ${sy} L ${tx} ${ty}`;
}

/**
 * Builds the hollow arc used for an expanded alternative group. The arc joins the two outermost
 * visible child links close to their shared parent, matching the diagram's left-to-right layout.
 * Collapsed groups and groups with fewer than two visible alternatives do not get a marker.
 */
function alternativeGroupMarker(node: HierarchyPointNode<DiagramTreeData>): DiagramGroupMarker | undefined {
    const children = node.children ?? [];
    if (node.data.incomingRelation?.groupType !== 'alternative' || children.length < 2) {
        return undefined;
    }

    const origin = { x: node.y + NODE_WIDTH / 2, y: node.x };
    const firstChild = children[0];
    const lastChild = children[children.length - 1];
    const start = pointOnLink(origin, firstChild);
    const end = pointOnLink(origin, lastChild);

    return {
        id: node.data.feature.id,
        path: `M ${start.x} ${start.y} A ${GROUP_MARKER_RADIUS} ${GROUP_MARKER_RADIUS} 0 0 1 ${end.x} ${end.y}`,
        label: `${node.data.feature.name}: choose exactly one alternative`,
    };
}

function pointOnLink(origin: DiagramPoint, child: HierarchyPointNode<DiagramTreeData>): DiagramPoint {
    const target = { x: child.y - NODE_WIDTH / 2, y: child.x };
    const horizontalDistance = target.x - origin.x;
    const verticalDistance = target.y - origin.y;
    const linkLength = Math.hypot(horizontalDistance, verticalDistance);
    const scale = GROUP_MARKER_RADIUS / linkLength;

    return {
        x: origin.x + horizontalDistance * scale,
        y: origin.y + verticalDistance * scale,
    };
}

function truncate(value: string, limit: number): string {
    if (value.length <= limit) {
        return value;
    }
    return `${value.slice(0, limit - 1).trimEnd()}…`;
}
