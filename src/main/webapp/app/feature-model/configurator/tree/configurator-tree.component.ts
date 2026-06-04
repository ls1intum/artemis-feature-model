import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';

import {
    collectAncestorIds,
    collectExpandableNodeIds,
    filterTreeByQuery,
    findNodeById,
} from '../../core/feature-model-tree.utils';
import { Feature, FeatureTreeNode, IncomingRelation } from '../../core/feature-model.types';
import { FeatureModelDiagramComponent } from '../../explorer/feature-model-diagram.component';
import { LocalizedViolation, LocalizedWarning } from '../shared/configurator-view.types';

@Component({
    selector: 'fm-configurator-tree',
    standalone: true,
    imports: [FeatureModelDiagramComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    templateUrl: './configurator-tree.component.html',
    styleUrl: './configurator-tree.component.scss',
})
export class ConfiguratorTreeComponent {
    readonly tree = input.required<FeatureTreeNode | null>();
    readonly selectedFeatureIds = input.required<ReadonlySet<string>>();
    readonly selectableFeatureIds = input.required<ReadonlySet<string>>();
    readonly violationIds = input.required<ReadonlySet<string>>();
    readonly warningIds = input.required<ReadonlySet<string>>();
    readonly localizedViolations = input.required<LocalizedViolation[]>();
    readonly localizedWarnings = input.required<LocalizedWarning[]>();
    readonly validationLoading = input.required<boolean>();
    readonly validationErrorMessage = input<string | undefined>(undefined);
    readonly hasValidationResult = input.required<boolean>();
    readonly isValid = input.required<boolean>();

    readonly selectionChange = output<ReadonlySet<string>>();
    readonly closeTree = output<void>();

    readonly searchQuery = signal<string>('');
    readonly selectedFeatureId = signal<string | undefined>(undefined);
    private readonly userExpandedIds = signal<ReadonlySet<string>>(new Set<string>());
    private primedRootId: string | undefined;

    constructor() {
        effect(() => {
            const root = this.tree();
            if (!root || this.primedRootId === root.feature.id) {
                return;
            }
            this.primedRootId = root.feature.id;
            this.selectedFeatureId.set(root.feature.id);
            this.userExpandedIds.set(new Set<string>([root.feature.id]));
        });
    }

    readonly filterResult = computed(() => filterTreeByQuery(this.tree(), this.searchQuery()));
    readonly matchedIds = computed<ReadonlySet<string>>(() => this.filterResult().matchedIds);
    readonly matchCount = computed(() => this.matchedIds().size);
    readonly hasActiveSearch = computed(() => this.searchQuery().trim().length > 0);
    readonly expandableIds = computed(() => collectExpandableNodeIds(this.tree()));
    readonly allExpanded = computed(() => {
        const expandable = this.expandableIds();
        if (expandable.length === 0) {
            return false;
        }
        const expanded = this.userExpandedIds();
        return expandable.every((id) => expanded.has(id));
    });
    readonly forcedExpandedIds = computed<ReadonlySet<string>>(() => {
        const flagged = new Set<string>([...this.violationIds(), ...this.warningIds()]);
        return collectAncestorIds(this.tree(), flagged);
    });
    readonly effectiveExpandedIds = computed<ReadonlySet<string>>(() => {
        const combined = new Set(this.userExpandedIds());
        for (const id of this.forcedExpandedIds()) {
            combined.add(id);
        }
        for (const id of this.filterResult().ancestorIds) {
            combined.add(id);
        }
        return combined;
    });
    readonly selectedNode = computed<FeatureTreeNode | null>(() => {
        const id = this.selectedFeatureId();
        return id ? findNodeById(this.tree(), id) : null;
    });
    readonly selectedFeature = computed<Feature | null>(() => this.selectedNode()?.feature ?? null);
    readonly selectedIncomingRelation = computed<IncomingRelation | null>(() => this.selectedNode()?.incomingRelation ?? null);
    readonly isSelectedFeatureToggleable = computed(() => {
        const feature = this.selectedFeature();
        return Boolean(feature && this.selectableFeatureIds().has(feature.id));
    });
    readonly isSelectedFeatureEnabled = computed(() => {
        const id = this.selectedFeatureId();
        return Boolean(id && this.selectedFeatureIds().has(id));
    });

    onSelectFeature(id: string): void {
        this.selectedFeatureId.set(id);
    }

    onToggleSelection(id: string): void {
        if (!this.selectableFeatureIds().has(id)) {
            return;
        }
        const next = new Set(this.selectedFeatureIds());
        if (next.has(id)) {
            next.delete(id);
        } else {
            next.add(id);
        }
        this.selectionChange.emit(next);
    }

    onToggleExpand(id: string): void {
        const next = new Set(this.userExpandedIds());
        if (next.has(id)) {
            next.delete(id);
        } else {
            next.add(id);
        }
        this.userExpandedIds.set(next);
    }

    onExpandAll(): void {
        this.userExpandedIds.set(new Set(this.expandableIds()));
    }

    onCollapseAll(): void {
        const rootId = this.tree()?.feature.id;
        this.userExpandedIds.set(rootId ? new Set<string>([rootId]) : new Set<string>());
    }

    onSearchInput(value: string): void {
        this.searchQuery.set(value);
        this.realignSelectionToMatches();
    }

    onClearSearch(): void {
        this.searchQuery.set('');
    }

    private realignSelectionToMatches(): void {
        const filtered = this.filterResult().tree;
        if (!filtered) {
            return;
        }
        const selectedId = this.selectedFeatureId();
        if (selectedId && findNodeById(filtered, selectedId)) {
            return;
        }
        const firstMatch = this.matchedIds().values().next().value;
        if (firstMatch) {
            this.selectedFeatureId.set(firstMatch);
        }
    }
}
