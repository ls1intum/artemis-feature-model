import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { FeatureModelService } from '../api/feature-model.service';
import { collectExpandableNodeIds, countTreeNodes, findNodeById } from '../core/feature-model-tree.utils';
import { Feature, FeatureModelResponse, FeatureTreeNode, IncomingRelation } from '../core/feature-model.types';
import { FeatureModelDiagramComponent } from '../explorer/feature-model-diagram.component';

const DEFAULT_ERROR_MESSAGE = 'Failed to load the feature model. Please verify that the server is running and try again.';

@Component({
    selector: 'fm-feature-model-configurator',
    standalone: true,
    imports: [FeatureModelDiagramComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    templateUrl: './feature-model-configurator.component.html',
    styleUrl: './feature-model-configurator.component.scss',
})
export class FeatureModelConfiguratorComponent implements OnInit {
    private readonly featureModelService = inject(FeatureModelService);
    private readonly destroyRef = inject(DestroyRef);

    readonly loading = signal<boolean>(true);
    readonly errorMessage = signal<string | undefined>(undefined);
    readonly response = signal<FeatureModelResponse | undefined>(undefined);
    readonly selectedFeatureIds = signal<ReadonlySet<string>>(new Set<string>());
    readonly selectedFeatureId = signal<string | undefined>(undefined);
    private readonly userExpandedIds = signal<ReadonlySet<string>>(new Set<string>());

    readonly model = computed(() => this.response()?.model);
    readonly tree = computed<FeatureTreeNode | null>(() => this.response()?.tree ?? null);
    readonly featureCount = computed(() => countTreeNodes(this.tree()));

    readonly defaultSelectedFeatureIds = computed<ReadonlySet<string>>(
        () => new Set(this.response()?.defaultSelectedFeatureIds ?? []),
    );

    readonly selectableFeatureIds = computed<ReadonlySet<string>>(() => {
        const features = this.response()?.features ?? [];
        const ids = new Set<string>();
        for (const feature of features) {
            if (feature.selectable) {
                ids.add(feature.id);
            }
        }
        return ids;
    });

    readonly selectedCount = computed(() => this.selectedFeatureIds().size);
    readonly selectableCount = computed(() => this.selectableFeatureIds().size);

    readonly matchedIds = computed<ReadonlySet<string>>(() => new Set<string>());

    readonly expandableIds = computed(() => collectExpandableNodeIds(this.tree()));

    readonly effectiveExpandedIds = computed<ReadonlySet<string>>(() => new Set(this.userExpandedIds()));

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

    readonly selectedFeature = computed<Feature | null>(() => this.selectedNode()?.feature ?? null);
    readonly selectedIncomingRelation = computed<IncomingRelation | null>(() => this.selectedNode()?.incomingRelation ?? null);

    readonly isSelectedFeatureToggleable = computed(() => {
        const feature = this.selectedFeature();
        return Boolean(feature?.selectable);
    });

    readonly isSelectedFeatureEnabled = computed(() => {
        const id = this.selectedFeatureId();
        return Boolean(id && this.selectedFeatureIds().has(id));
    });

    readonly isSelectedFeatureDefaultSelected = computed(() => {
        const id = this.selectedFeatureId();
        return Boolean(id && this.defaultSelectedFeatureIds().has(id));
    });

    readonly defaultStateBadgeClass = computed(() => defaultStateBadgeClass(this.selectedFeature()?.defaultState ?? null));
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

    /**
     * Toggles the expand/collapse state of a single branch by flipping membership of `id` in
     * `userExpandedIds`.
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
        const rootId = this.tree()?.feature.id;
        this.userExpandedIds.set(rootId ? new Set<string>([rootId]) : new Set<string>());
    }

    /**
     * Installs the loaded model into the component state and primes the configurator: the user
     * selection is seeded from `defaultSelectedFeatureIds`, only the root branch is expanded so
     * the diagram does not overwhelm on first paint, and the root focuses the details panel.
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
        this.selectedFeatureIds.set(new Set<string>(response.defaultSelectedFeatureIds));
    }

    /**
     * Surfaces the API failure to the user by storing the error message (or a generic fallback when
     * the error has no usable message) and clearing the loading flag so the error panel renders.
     *
     * @param error Error emitted by the `FeatureModelService` observable.
     */
    private handleError(error: Error): void {
        const message = error?.message?.trim();
        this.errorMessage.set(message && message.length > 0 ? message : DEFAULT_ERROR_MESSAGE);
        this.loading.set(false);
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
