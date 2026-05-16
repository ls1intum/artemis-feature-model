import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';

import { FeatureModelService } from '../api/feature-model.service';
import { collectExpandableNodeIds, countTreeNodes, filterTreeByQuery, findNodeById } from '../core/feature-model-tree.utils';
import { FeatureModelResponse, FeatureTreeNode, IncomingRelation } from '../core/feature-model.types';
import { FeatureModelDiagramComponent } from './feature-model-diagram.component';
import { FeatureModelTreeNodeComponent } from './feature-model-tree-node.component';

const DEFAULT_ERROR_MESSAGE = 'Failed to load the feature model. Please verify that the server is running and try again.';

export type ExplorerViewMode = 'list' | 'diagram';

@Component({
    selector: 'fm-feature-model-explorer',
    standalone: true,
    imports: [FormsModule, FeatureModelTreeNodeComponent, FeatureModelDiagramComponent],
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
    readonly relationBadgeClass = computed(() => relationBadgeClass(this.selectedIncomingRelation()?.relationType));

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

    private handleLoaded(response: FeatureModelResponse): void {
        this.response.set(response);
        this.errorMessage.set(undefined);
        this.loading.set(false);
        const rootId = response.tree.feature.id;
        this.userExpandedIds.set(new Set<string>([rootId]));
        this.selectedFeatureId.set(rootId);
    }

    private handleError(error: Error): void {
        const message = error?.message?.trim();
        this.errorMessage.set(message && message.length > 0 ? message : DEFAULT_ERROR_MESSAGE);
        this.loading.set(false);
    }

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

function relationBadgeClass(relationType: string | undefined): string {
    switch (relationType) {
        case 'mandatory':
            return 'text-bg-primary';
        case 'optional':
            return 'text-bg-warning';
        case 'group':
            return 'text-bg-info';
        default:
            return 'text-bg-light border';
    }
}
