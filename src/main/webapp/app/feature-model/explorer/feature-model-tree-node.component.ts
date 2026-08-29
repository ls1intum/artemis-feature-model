import { ChangeDetectionStrategy, Component, computed, forwardRef, input, output } from '@angular/core';

import { featureKindDotClass, featureKindLabel } from '../core/feature-model-tree.utils';
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
    readonly isStructural = computed(() => !this.node().feature.selectable);
    readonly isDefaultEnabled = computed(() => this.node().feature.defaultState === 'enabled');
    readonly kindDotClass = computed(() => featureKindDotClass(this.node().feature.kind));
    /** Accessible name for the colour dot; it carries kind and selectability, which no longer have badges. */
    readonly kindTitle = computed(() => {
        const kind = featureKindLabel(this.node().feature.kind, this.node().feature.category);
        return this.isStructural() ? `${kind} · structural` : kind;
    });

    /**
     * Emits `toggleExpand` for this node and stops the click from bubbling to the row, which would
     * otherwise also fire `selectFeature` (the row and the toggle button live in the same element).
     *
     * @param event Click or keyboard event from the expand/collapse button.
     */
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
