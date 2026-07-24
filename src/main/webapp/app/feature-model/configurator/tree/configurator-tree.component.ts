import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';

import {
    collectAncestorIds,
    collectExpandableNodeIds,
    filterTreeByQuery,
    findNodeById,
} from '../../core/feature-model-tree.utils';
import { DeploymentProfileSummary, FeatureAvailability } from '../../core/deployment-profile.types';
import { Feature, FeatureTreeNode, IncomingRelation } from '../../core/feature-model.types';
import { GuidedDecisionOption } from '../../core/guided-workflow.types';
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
    readonly guidedOptions = input.required<GuidedDecisionOption[]>();
    readonly featureAvailabilityById = input.required<ReadonlyMap<string, FeatureAvailability>>();
    readonly activeProfile = input<DeploymentProfileSummary | undefined>(undefined);
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
            // Prime selection and expansion once per loaded tree, without overriding later user navigation.
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
    /** Keeps validation-related branches visible even if the user has not manually expanded them. */
    readonly forcedExpandedIds = computed<ReadonlySet<string>>(() => {
        const flagged = new Set<string>([...this.violationIds(), ...this.warningIds()]);
        return collectAncestorIds(this.tree(), flagged);
    });
    /** Merges manual expansion, search expansion, and validation expansion into the tree's rendered state. */
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
    readonly treeToggleableFeatureIds = computed<ReadonlySet<string>>(() => {
        const toggleable = new Set(this.selectableFeatureIds());
        collectLockedTechnicalFeatureIds(this.tree(), toggleable);
        return toggleable;
    });
    readonly isSelectedFeatureToggleable = computed(() => {
        const feature = this.selectedFeature();
        return Boolean(feature && this.treeToggleableFeatureIds().has(feature.id));
    });
    readonly isSelectedFeatureLocked = computed(() => {
        const feature = this.selectedFeature();
        return Boolean(feature && this.selectableFeatureIds().has(feature.id) && !this.treeToggleableFeatureIds().has(feature.id));
    });
    readonly selectedAlternativeGroup = computed<FeatureTreeNode | null>(() => {
        const selectedId = this.selectedFeatureId();
        return selectedId ? findAlternativeGroup(this.tree(), selectedId) : null;
    });
    readonly isSelectedFeatureAlternative = computed(() => this.selectedAlternativeGroup() !== null);
    readonly canClearSelectedFeature = computed(() => {
        const group = this.selectedAlternativeGroup();
        if (!group || !this.isSelectedFeatureEnabled()) {
            return true;
        }
        return group.children.some((child) => child.feature.id !== this.selectedFeatureId() && this.selectedFeatureIds().has(child.feature.id));
    });
    readonly isSelectedFeatureEnabled = computed(() => {
        const id = this.selectedFeatureId();
        return Boolean(id && this.selectedFeatureIds().has(id));
    });
    /** Finds guided choices that can explain why the selected feature is on or off. */
    readonly relatedGuidedOptions = computed<GuidedDecisionOption[]>(() => {
        const id = this.selectedFeatureId();
        if (!id) {
            return [];
        }
        return this.guidedOptions().filter((option) => option.selects.includes(id) || option.deselects.includes(id));
    });
    /** Shows technical prerequisites from both the raw feature and any related guided options. */
    readonly relatedCapabilityRequirements = computed<string[]>(() => {
        const requirements = new Set<string>(this.selectedFeature()?.requiresCapabilities ?? []);
        for (const option of this.relatedGuidedOptions()) {
            for (const capability of option.requiresCapabilities) {
                requirements.add(capability);
            }
        }
        return [...requirements];
    });
    /** Profile-aware availability of the selected feature, for the advanced debug view. */
    readonly selectedFeatureAvailability = computed<FeatureAvailability | undefined>(() => {
        const id = this.selectedFeatureId();
        return id ? this.featureAvailabilityById().get(id) : undefined;
    });
    /** Shows artifact impact metadata only in the advanced tree view. */
    readonly relatedArtifactImpacts = computed<string[]>(() => {
        const impacts = new Set<string>();
        for (const option of this.relatedGuidedOptions()) {
            for (const impact of option.artifactImpacts) {
                impacts.add(impact);
            }
        }
        return [...impacts];
    });

    onSelectFeature(id: string): void {
        this.selectedFeatureId.set(id);
    }

    /** Emits a complete replacement set because selection ownership stays in the parent configurator. */
    onToggleSelection(id: string): void {
        if (!this.treeToggleableFeatureIds().has(id)) {
            return;
        }
        const next = new Set(this.selectedFeatureIds());
        const alternativeGroup = findAlternativeGroup(this.tree(), id);
        if (alternativeGroup) {
            this.toggleAlternative(next, alternativeGroup, id);
            return;
        }
        if (next.has(id)) {
            next.delete(id);
        } else {
            next.add(id);
        }
        this.selectionChange.emit(next);
    }

    /** Applies radio-style selection within an alternative group. */
    private toggleAlternative(selection: Set<string>, group: FeatureTreeNode, featureId: string): void {
        if (selection.has(featureId)) {
            const anotherAlternativeSelected = group.children.some(
                (child) => child.feature.id !== featureId && selection.has(child.feature.id),
            );
            if (!anotherAlternativeSelected) {
                return;
            }
            selection.delete(featureId);
            this.selectionChange.emit(selection);
            return;
        }
        for (const alternative of group.children) {
            selection.delete(alternative.feature.id);
        }
        selection.add(featureId);
        this.selectionChange.emit(selection);
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

    /** Updates the filter and moves focus if the current selected node is no longer visible. */
    onSearchInput(value: string): void {
        this.searchQuery.set(value);
        this.realignSelectionToMatches();
    }

    onClearSearch(): void {
        this.searchQuery.set('');
    }

    /** Keeps the detail panel attached to a visible match while the tree is filtered. */
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

/** Removes mandatory technical leaves from the diagram's toggleable id set. */
function collectLockedTechnicalFeatureIds(node: FeatureTreeNode | null, toggleableIds: Set<string>): void {
    if (!node) {
        return;
    }
    const mandatoryTechnicalLeaf =
        node.feature.category === 'technical' &&
        node.feature.selectable &&
        node.incomingRelation?.relationType === 'mandatory';
    if (mandatoryTechnicalLeaf) {
        toggleableIds.delete(node.feature.id);
    }
    for (const child of node.children) {
        collectLockedTechnicalFeatureIds(child, toggleableIds);
    }
}

/** Finds the alternative group that directly owns a feature id. */
function findAlternativeGroup(node: FeatureTreeNode | null, featureId: string): FeatureTreeNode | null {
    if (!node) {
        return null;
    }
    const isAlternativeGroup = node.incomingRelation?.groupType === 'alternative';
    const ownsFeature = node.children.some((child) => child.feature.id === featureId);
    if (isAlternativeGroup && ownsFeature) {
        return node;
    }
    for (const child of node.children) {
        const match = findAlternativeGroup(child, featureId);
        if (match) {
            return match;
        }
    }
    return null;
}
