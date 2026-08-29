import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';

import { FeatureModelService } from '../api/feature-model.service';
import {
    collectExpandableNodeIds,
    countTreeNodes,
    featureKindDotClass,
    featureKindLabel,
    filterTreeByQuery,
    findNodeById,
    formatFeatureCategory,
} from '../core/feature-model-tree.utils';
import { FeatureModelResponse, FeatureTreeNode, IncomingRelation } from '../core/feature-model.types';
import { FeatureModelDiagramComponent } from './feature-model-diagram.component';
import { FeatureModelSnapshotsComponent } from './feature-model-snapshots.component';
import { FeatureModelTreeNodeComponent } from './feature-model-tree-node.component';

const DEFAULT_ERROR_MESSAGE = 'Failed to load the feature model. Please verify that the server is running and try again.';

export type ExplorerViewMode = 'list' | 'diagram';

/** Fixed display order for the kind legend; kinds outside it keep their model order at the end. */
const KIND_ORDER = ['root', 'group', 'module', 'feature'];

export interface KindLegendEntry {
    /** Identity of the rendered swatch: one entry per dot colour and label pair. */
    key: string;
    kind: string;
    label: string;
    dotClass: string;
    hollow: boolean;
}

@Component({
    selector: 'fm-feature-model-explorer',
    standalone: true,
    imports: [FormsModule, FeatureModelTreeNodeComponent, FeatureModelDiagramComponent, FeatureModelSnapshotsComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    templateUrl: './feature-model-explorer.component.html',
    styleUrl: './feature-model-explorer.component.scss',
})
export class FeatureModelExplorerComponent implements OnInit {
    private readonly featureModelService = inject(FeatureModelService);
    private readonly destroyRef = inject(DestroyRef);

    readonly loading = signal<boolean>(true);
    readonly errorMessage = signal<string | undefined>(undefined);
    readonly response = signal<FeatureModelResponse | undefined>(undefined);
    readonly searchQuery = signal<string>('');
    readonly selectedFeatureId = signal<string | undefined>(undefined);
    readonly viewMode = signal<ExplorerViewMode>('list');
    /** Keeps the maintainer-only source metadata collapsed until it is asked for. */
    readonly sourceExpanded = signal<boolean>(false);
    private readonly userExpandedIds = signal<ReadonlySet<string>>(new Set<string>());

    readonly model = computed(() => this.response()?.model);
    readonly tree = computed<FeatureTreeNode | null>(() => this.response()?.tree ?? null);
    readonly featureCount = computed(() => countTreeNodes(this.tree()));
    readonly relationCount = computed(() => this.response()?.relations.length ?? 0);
    readonly constraintCount = computed(() => this.response()?.constraints.length ?? 0);
    readonly warnings = computed(() => this.response()?.warnings ?? []);
    readonly defaultSelectedCount = computed(() => this.response()?.defaultSelectedFeatureIds.length ?? 0);

    readonly filterResult = computed(() => filterTreeByQuery(this.tree(), this.searchQuery()));
    readonly visibleTree = computed(() => this.filterResult().tree);
    readonly matchedIds = computed(() => this.filterResult().matchedIds);
    readonly matchCount = computed(() => this.filterResult().matchedIds.size);
    readonly hasActiveSearch = computed(() => this.searchQuery().trim().length > 0);
    readonly isListView = computed(() => this.viewMode() === 'list');
    readonly isDiagramView = computed(() => this.viewMode() === 'diagram');

    readonly expandableIds = computed(() => collectExpandableNodeIds(this.tree()));

    readonly effectiveExpandedIds = computed<ReadonlySet<string>>(() => {
        const combined = new Set(this.userExpandedIds());
        for (const ancestor of this.filterResult().ancestorIds) {
            combined.add(ancestor);
        }
        return combined;
    });

    readonly allExpanded = computed(() => {
        const expandable = this.expandableIds();
        if (expandable.length === 0) {
            return false;
        }
        const expanded = this.userExpandedIds();
        for (const id of expandable) {
            if (!expanded.has(id)) {
                return false;
            }
        }
        return true;
    });

    readonly selectedNode = computed<FeatureTreeNode | null>(() => {
        const id = this.selectedFeatureId();
        if (!id) {
            return null;
        }
        return findNodeById(this.tree(), id);
    });

    readonly selectedIncomingRelation = computed<IncomingRelation | null>(() => this.selectedNode()?.incomingRelation ?? null);
    readonly hasSourceMetadata = computed(() => Boolean(this.selectedNode()?.feature.source));
    readonly isDefaultSelected = computed(() => {
        const id = this.selectedFeatureId();
        const defaults = this.response()?.defaultSelectedFeatureIds ?? [];
        return Boolean(id && defaults.includes(id));
    });
    readonly defaultStateBadgeClass = computed(() => defaultStateBadgeClass(this.selectedNode()?.feature.defaultState ?? null));
    /**
     * Legend built from what the rows actually render: one entry per dot colour and label pair present in
     * the loaded model. A swatch is drawn hollow only when every feature behind it is structural, so it can
     * never show a fill the rows do not.
     */
    readonly kindLegend = computed<KindLegendEntry[]>(() => {
        const selectableByKey = new Map<string, { entry: KindLegendEntry; anySelectable: boolean }>();
        for (const feature of this.response()?.features ?? []) {
            const label = featureKindLabel(feature.kind, feature.category);
            const key = `${feature.kind}|${label}`;
            const seen = selectableByKey.get(key);
            if (seen) {
                seen.anySelectable ||= feature.selectable;
                continue;
            }
            selectableByKey.set(key, {
                entry: { key, kind: feature.kind, label, dotClass: featureKindDotClass(feature.kind), hollow: false },
                anySelectable: feature.selectable,
            });
        }
        return [...selectableByKey.values()]
            .map(({ entry, anySelectable }) => ({ ...entry, hollow: !anySelectable }))
            .sort((left, right) => kindRank(left.kind) - kindRank(right.kind));
    });
    readonly hasStructuralKinds = computed(() => this.kindLegend().some((entry) => entry.hollow));
    readonly selectedKindLabel = computed(() => featureKindLabel(this.selectedNode()?.feature.kind ?? '', this.selectedNode()?.feature.category ?? ''));
    readonly selectedCategoryLabel = computed(() => formatFeatureCategory(this.selectedNode()?.feature.category ?? ''));
    readonly selectedKindDotClass = computed(() => featureKindDotClass(this.selectedNode()?.feature.kind ?? ''));

    ngOnInit(): void {
        this.featureModelService
            .loadFeatureModel()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (response) => this.handleLoaded(response),
                error: (error: Error) => this.handleError(error),
            });
    }

    onSelectFeature(id: string): void {
        this.selectedFeatureId.set(id);
    }

    /**
     * Toggles the expand/collapse state of a single branch by flipping membership of `id` in
     * `userExpandedIds`. Allocates a new `Set` so the signal sees a fresh reference.
     *
     * @param id Feature id whose subtree should be expanded if collapsed, or collapsed if expanded.
     */
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
        this.userExpandedIds.set(new Set<string>());
    }

    /**
     * Updates the search query signal and, if the previously selected feature is now hidden by the
     * filter, moves the selection onto the first remaining match so the details panel does not
     * point at an invisible node.
     *
     * @param value Raw value from the search input.
     */
    onSearchInput(value: string): void {
        this.searchQuery.set(value);
        this.realignSelectionToVisibleTree();
    }

    onClearSearch(): void {
        this.searchQuery.set('');
    }

    onSetViewMode(mode: ExplorerViewMode): void {
        this.viewMode.set(mode);
    }

    onToggleSourceMetadata(): void {
        this.sourceExpanded.update((expanded) => !expanded);
    }

    /**
     * Installs the loaded model into the component state and primes the explorer so the user lands
     * on the root: expansion contains only the root id (groups stay collapsed), the details panel
     * focuses the root, and any earlier loading/error state is cleared.
     *
     * @param response Successful payload from `GET /api/feature-model`.
     */
    private handleLoaded(response: FeatureModelResponse): void {
        this.response.set(response);
        this.errorMessage.set(undefined);
        this.loading.set(false);
        const rootId = response.tree.feature.id;
        this.userExpandedIds.set(new Set<string>([rootId]));
        this.selectedFeatureId.set(rootId);
    }

    /**
     * Surfaces the API failure to the user by storing the error message (or a generic fallback
     * when the error has no usable message) and clearing the loading flag so the error panel renders.
     *
     * @param error Error emitted by the `FeatureModelService` observable.
     */
    private handleError(error: Error): void {
        const message = error?.message?.trim();
        this.errorMessage.set(message && message.length > 0 ? message : DEFAULT_ERROR_MESSAGE);
        this.loading.set(false);
    }

    /**
     * Keeps the details panel pointing at a visible node after the search query changes: if the
     * currently selected feature is still in the filtered tree (or nothing matches), selection is
     * left alone; otherwise the first matching id becomes the new selection.
     */
    private realignSelectionToVisibleTree(): void {
        const visibleRoot = this.visibleTree();
        if (!visibleRoot) {
            return;
        }
        const selectedId = this.selectedFeatureId();
        if (selectedId && findNodeById(visibleRoot, selectedId)) {
            return;
        }
        const firstMatch = this.filterResult().matchedIds.values().next().value;
        if (firstMatch) {
            this.selectedFeatureId.set(firstMatch);
        }
    }
}

function defaultStateBadgeClass(state: string | null): string {
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

function kindRank(kind: string): number {
    const index = KIND_ORDER.indexOf(kind);
    return index === -1 ? KIND_ORDER.length : index;
}
