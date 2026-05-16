import { ChangeDetectionStrategy, Component, computed, forwardRef, input, output } from '@angular/core';

import { FeatureTreeNode } from '../core/feature-model.types';

@Component({
    selector: 'fm-feature-model-tree-node',
    standalone: true,
    imports: [forwardRef(() => FeatureModelTreeNodeComponent)],
    changeDetection: ChangeDetectionStrategy.OnPush,
    templateUrl: './feature-model-tree-node.component.html',
    styleUrl: './feature-model-tree-node.component.scss',
})
export class FeatureModelTreeNodeComponent {
    readonly node = input.required<FeatureTreeNode>();
    readonly expandedIds = input.required<ReadonlySet<string>>();
    readonly matchedIds = input.required<ReadonlySet<string>>();
    readonly selectedId = input<string | undefined>(undefined);
    readonly depth = input<number>(0);

    readonly toggleExpand = output<string>();
    readonly selectFeature = output<string>();

    readonly featureId = computed(() => this.node().feature.id);
    readonly hasChildren = computed(() => this.node().children.length > 0);
    readonly isExpanded = computed(() => this.expandedIds().has(this.featureId()));
    readonly isSelected = computed(() => this.selectedId() === this.featureId());
    readonly isMatched = computed(() => this.matchedIds().has(this.featureId()));
    readonly nextDepth = computed(() => this.depth() + 1);
    readonly kindLabel = computed(() => formatKind(this.node().feature.kind));
    readonly relationLabel = computed(() => formatRelation(this.node()));
    readonly defaultStateLabel = computed(() => formatDefaultState(this.node().feature.defaultState));
    readonly defaultStateBadgeClass = computed(() => formatDefaultStateBadgeClass(this.node().feature.defaultState));

    onToggle(event: Event): void {
        event.stopPropagation();
        this.toggleExpand.emit(this.featureId());
    }

    onSelect(): void {
        this.selectFeature.emit(this.featureId());
    }

    onChildToggle(id: string): void {
        this.toggleExpand.emit(id);
    }

    onChildSelect(id: string): void {
        this.selectFeature.emit(id);
    }
}

function formatKind(kind: string): string {
    switch (kind) {
        case 'root':
            return 'Root';
        case 'group':
            return 'Group';
        case 'module':
            return 'Module';
        case 'feature':
            return 'Feature';
        default:
            return kind;
    }
}

function formatRelation(node: FeatureTreeNode): string | null {
    const incoming = node.incomingRelation;
    if (!incoming) {
        return null;
    }
    const base = capitalize(incoming.relationType);
    if (incoming.relationType === 'group' && incoming.groupType) {
        return `${base} (${incoming.groupType})`;
    }
    return base;
}

function formatDefaultState(state: string | null): string | null {
    if (!state) {
        return null;
    }
    if (state === 'not_applicable') {
        return 'Not applicable';
    }
    return capitalize(state);
}

function formatDefaultStateBadgeClass(state: string | null): string {
    switch (state) {
        case 'enabled':
            return 'text-bg-success';
        case 'disabled':
            return 'text-bg-light border';
        case 'not_applicable':
            return 'text-bg-secondary';
        default:
            return 'text-bg-light border';
    }
}

function capitalize(value: string): string {
    if (value.length === 0) {
        return value;
    }
    return value.charAt(0).toUpperCase() + value.slice(1);
}
