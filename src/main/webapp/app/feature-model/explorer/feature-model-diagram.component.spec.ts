import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { collectExpandableNodeIds } from '../core/feature-model-tree.utils';
import { buildMvpFeatureModelResponse } from '../core/feature-model.test-fixtures';
import { FeatureTreeNode } from '../core/feature-model.types';
import { FeatureModelDiagramComponent } from './feature-model-diagram.component';

interface FixtureOptions {
    expandedIds?: ReadonlySet<string>;
    matchedIds?: ReadonlySet<string>;
    selectedId?: string;
    selectedFeatureIds?: ReadonlySet<string>;
    violationIds?: ReadonlySet<string>;
    warningIds?: ReadonlySet<string>;
    toggleableIds?: ReadonlySet<string>;
    configurationMode?: boolean;
}

function createFixture(tree: FeatureTreeNode, options: FixtureOptions = {}): ComponentFixture<FeatureModelDiagramComponent> {
    TestBed.configureTestingModule({ imports: [FeatureModelDiagramComponent] });
    const fixture = TestBed.createComponent(FeatureModelDiagramComponent);
    const expanded = options.expandedIds ?? new Set(collectExpandableNodeIds(tree));
    fixture.componentRef.setInput('tree', tree);
    fixture.componentRef.setInput('expandedIds', expanded);
    fixture.componentRef.setInput('matchedIds', options.matchedIds ?? new Set());
    fixture.componentRef.setInput('selectedId', options.selectedId);
    if (options.selectedFeatureIds !== undefined) {
        fixture.componentRef.setInput('selectedFeatureIds', options.selectedFeatureIds);
    }
    if (options.violationIds !== undefined) {
        fixture.componentRef.setInput('violationIds', options.violationIds);
    }
    if (options.warningIds !== undefined) {
        fixture.componentRef.setInput('warningIds', options.warningIds);
    }
    if (options.toggleableIds !== undefined) {
        fixture.componentRef.setInput('toggleableIds', options.toggleableIds);
    }
    if (options.configurationMode !== undefined) {
        fixture.componentRef.setInput('configurationMode', options.configurationMode);
    }
    fixture.detectChanges();
    return fixture;
}

function root(fixture: ComponentFixture<FeatureModelDiagramComponent>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
}

function nodeFor(fixture: ComponentFixture<FeatureModelDiagramComponent>, featureId: string): Element {
    const node = root(fixture).querySelector(`.diagram-node[data-feature-id="${featureId}"]`);
    if (!node) {
        throw new Error(`No diagram node for ${featureId}`);
    }
    return node;
}

function toggleFor(fixture: ComponentFixture<FeatureModelDiagramComponent>, featureId: string): Element {
    const toggle = root(fixture).querySelector(`.diagram-toggle[data-feature-id="${featureId}"]`);
    if (!toggle) {
        throw new Error(`No toggle for ${featureId}`);
    }
    return toggle;
}

describe('FeatureModelDiagramComponent', () => {
    let mvpTree: FeatureTreeNode;

    beforeEach(() => {
        mvpTree = buildMvpFeatureModelResponse().tree;
    });

    it('renders one group per feature when every branch is expanded', () => {
        const fixture = createFixture(mvpTree);
        const nodes = root(fixture).querySelectorAll('.diagram-node');
        expect(nodes).toHaveLength(24);
    });

    it('renders an SVG with a numeric viewBox', () => {
        const fixture = createFixture(mvpTree);
        const svg = root(fixture).querySelector('.diagram-svg');
        expect(svg).not.toBeNull();
        const viewBox = svg?.getAttribute('viewBox') ?? '';
        const parts = viewBox.split(' ').map(Number);
        expect(parts).toHaveLength(4);
        expect(parts.every((value) => Number.isFinite(value))).toBe(true);
    });

    it('lays out siblings vertically (LTR orientation)', () => {
        const fixture = createFixture(mvpTree);
        const lecture = nodeFor(fixture, 'lecture');
        const tutorialgroup = nodeFor(fixture, 'tutorialgroup');
        const lectureTransform = lecture.getAttribute('transform') ?? '';
        const tutorialTransform = tutorialgroup.getAttribute('transform') ?? '';
        const [lectureX, lectureY] = parseTranslate(lectureTransform);
        const [tutorialX, tutorialY] = parseTranslate(tutorialTransform);
        expect(lectureX).toBeCloseTo(tutorialX, 2);
        expect(tutorialY).toBeGreaterThan(lectureY);
    });

    it('marks mandatory relations with a filled circle and optional relations with a hollow circle', () => {
        const fixture = createFixture(mvpTree);
        const mandatoryMarkers = root(fixture).querySelectorAll('.diagram-marker--mandatory');
        const optionalMarkers = root(fixture).querySelectorAll('.diagram-marker--optional');

        const mandatoryCount = mvpTree.children
            .flatMap((child) => child.children)
            .filter((child) => child.incomingRelation?.relationType === 'mandatory').length;
        const optionalCount = mvpTree.children
            .flatMap((child) => child.children)
            .filter((child) => child.incomingRelation?.relationType === 'optional').length;

        expect(mandatoryMarkers).toHaveLength(mandatoryCount);
        expect(optionalMarkers).toHaveLength(optionalCount);
    });

    it('emits selectFeature when a node is clicked', () => {
        const fixture = createFixture(mvpTree);
        let emitted: string | undefined;
        fixture.componentInstance.selectFeature.subscribe((id: string) => {
            emitted = id;
        });

        (nodeFor(fixture, 'lecture') as HTMLElement).dispatchEvent(new Event('click'));
        expect(emitted).toBe('lecture');
    });

    it('applies the selected modifier to the currently selected node', () => {
        const fixture = createFixture(mvpTree, { selectedId: 'programming' });
        const selected = root(fixture).querySelector('.diagram-node--selected');
        expect(selected?.getAttribute('data-feature-id')).toBe('programming');
        expect(selected?.getAttribute('aria-selected')).toBe('true');
    });

    it('applies the match modifier to nodes in matchedIds', () => {
        const fixture = createFixture(mvpTree, { matchedIds: new Set(['lecture', 'iris']) });
        const matches = root(fixture).querySelectorAll('.diagram-node--match');
        const matchedIds = Array.from(matches).map((node) => node.getAttribute('data-feature-id'));
        expect(matchedIds).toEqual(expect.arrayContaining(['lecture', 'iris']));
        expect(matchedIds).toHaveLength(2);
    });

    it('renders one link per non-root visible node', () => {
        const fixture = createFixture(mvpTree);
        const links = root(fixture).querySelectorAll('.diagram-link');
        expect(links).toHaveLength(23);
    });

    it('exposes the full feature name and id via the SVG <title> tooltip', () => {
        const fixture = createFixture(mvpTree);
        const exerciseCommon = nodeFor(fixture, 'exercise-common');
        const title = exerciseCommon.querySelector('title');
        expect(title?.textContent).toBe('Exercise (Common / Umbrella Layer) (exercise-common)');
    });

    it('marks the root node so it can be styled differently', () => {
        const fixture = createFixture(mvpTree);
        const root_ = nodeFor(fixture, 'artemis');
        expect(root_.classList.contains('diagram-node--root')).toBe(true);
    });

    it('hides descendants and renders a +N badge for collapsed nodes', () => {
        const fixture = createFixture(mvpTree, { expandedIds: new Set(['artemis']) });
        const visibleIds = Array.from(root(fixture).querySelectorAll('.diagram-node')).map((node) =>
            node.getAttribute('data-feature-id'),
        );
        expect(visibleIds).toContain('artemis');
        expect(visibleIds).toContain('teaching-and-content');
        expect(visibleIds).not.toContain('lecture');

        const teachingToggle = toggleFor(fixture, 'teaching-and-content');
        expect(teachingToggle.classList.contains('diagram-toggle--collapsed')).toBe(true);
        const label = teachingToggle.querySelector('.diagram-toggle__label');
        expect(label?.textContent?.trim()).toBe('+4');
    });

    it('shows a collapse affordance with a minus glyph on expanded non-leaf nodes', () => {
        const fixture = createFixture(mvpTree);
        const artemisToggle = toggleFor(fixture, 'artemis');
        expect(artemisToggle.classList.contains('diagram-toggle--collapsed')).toBe(false);
        const label = artemisToggle.querySelector('.diagram-toggle__label');
        expect(label?.textContent?.trim()).toBe('−');
    });

    it('does not render a toggle for leaf nodes', () => {
        const fixture = createFixture(mvpTree);
        const lecture = nodeFor(fixture, 'lecture');
        expect(lecture.querySelector('.diagram-toggle')).toBeNull();
    });

    it('emits toggleExpand without firing selectFeature when the badge is clicked', () => {
        const fixture = createFixture(mvpTree, { expandedIds: new Set(['artemis']) });
        let toggled: string | undefined;
        let selected: string | undefined;
        fixture.componentInstance.toggleExpand.subscribe((id: string) => {
            toggled = id;
        });
        fixture.componentInstance.selectFeature.subscribe((id: string) => {
            selected = id;
        });

        const event = new MouseEvent('click', { bubbles: true, cancelable: true });
        toggleFor(fixture, 'teaching-and-content').dispatchEvent(event);

        expect(toggled).toBe('teaching-and-content');
        expect(selected).toBeUndefined();
    });

    it('applies the configured-selected modifier and indicator for ids in selectedFeatureIds', () => {
        const fixture = createFixture(mvpTree, { selectedFeatureIds: new Set(['lecture', 'programming']) });
        const lecture = nodeFor(fixture, 'lecture');
        const programming = nodeFor(fixture, 'programming');
        const tutorialgroup = nodeFor(fixture, 'tutorialgroup');

        expect(lecture.classList.contains('diagram-node--configured-selected')).toBe(true);
        expect(programming.classList.contains('diagram-node--configured-selected')).toBe(true);
        expect(tutorialgroup.classList.contains('diagram-node--configured-selected')).toBe(false);
        expect(lecture.querySelector('[data-testid="selected-indicator"]')).not.toBeNull();
    });

    it('applies the violation modifier and indicator for ids in violationIds', () => {
        const fixture = createFixture(mvpTree, { violationIds: new Set(['programming']) });
        const programming = nodeFor(fixture, 'programming');
        expect(programming.classList.contains('diagram-node--violation')).toBe(true);
        expect(programming.querySelector('[data-testid="violation-indicator"]')).not.toBeNull();
    });

    it('applies the warning modifier and indicator for ids in warningIds', () => {
        const fixture = createFixture(mvpTree, { warningIds: new Set(['iris']) });
        const iris = nodeFor(fixture, 'iris');
        expect(iris.classList.contains('diagram-node--warning')).toBe(true);
        expect(iris.querySelector('[data-testid="warning-indicator"]')).not.toBeNull();
    });

    it('renders only the violation indicator when a node is also selected and matched', () => {
        const fixture = createFixture(mvpTree, {
            selectedId: 'programming',
            matchedIds: new Set(['programming']),
            selectedFeatureIds: new Set(['programming']),
            violationIds: new Set(['programming']),
        });
        const programming = nodeFor(fixture, 'programming');

        expect(programming.classList.contains('diagram-node--violation')).toBe(true);
        expect(programming.classList.contains('diagram-node--selected')).toBe(true);
        expect(programming.classList.contains('diagram-node--match')).toBe(true);
        expect(programming.classList.contains('diagram-node--configured-selected')).toBe(true);

        // Only the violation indicator should appear; lower-precedence indicators are suppressed.
        expect(programming.querySelector('[data-testid="violation-indicator"]')).not.toBeNull();
        expect(programming.querySelector('[data-testid="warning-indicator"]')).toBeNull();
        expect(programming.querySelector('[data-testid="selected-indicator"]')).toBeNull();
    });

    it('marks toggleable nodes via the toggleable modifier class', () => {
        const fixture = createFixture(mvpTree, { toggleableIds: new Set(['lecture']) });
        const lecture = nodeFor(fixture, 'lecture');
        const teaching = nodeFor(fixture, 'teaching-and-content');
        expect(lecture.classList.contains('diagram-node--toggleable')).toBe(true);
        expect(teaching.classList.contains('diagram-node--toggleable')).toBe(false);
    });

    it('emits toggleSelection in configuration mode when a toggleable node is clicked', () => {
        const fixture = createFixture(mvpTree, {
            configurationMode: true,
            toggleableIds: new Set(['lecture']),
        });
        let toggled: string | undefined;
        let focused: string | undefined;
        fixture.componentInstance.toggleSelection.subscribe((id: string) => {
            toggled = id;
        });
        fixture.componentInstance.selectFeature.subscribe((id: string) => {
            focused = id;
        });

        (nodeFor(fixture, 'lecture') as HTMLElement).dispatchEvent(new Event('click'));

        expect(focused).toBe('lecture');
        expect(toggled).toBe('lecture');
    });

    it('does not emit toggleSelection in configuration mode when a structural node is clicked', () => {
        const fixture = createFixture(mvpTree, {
            configurationMode: true,
            toggleableIds: new Set(['lecture', 'programming']),
        });
        let toggled: string | undefined;
        let focused: string | undefined;
        fixture.componentInstance.toggleSelection.subscribe((id: string) => {
            toggled = id;
        });
        fixture.componentInstance.selectFeature.subscribe((id: string) => {
            focused = id;
        });

        (nodeFor(fixture, 'teaching-and-content') as HTMLElement).dispatchEvent(new Event('click'));

        expect(focused).toBe('teaching-and-content');
        expect(toggled).toBeUndefined();
    });

    it('does not emit toggleSelection when configuration mode is disabled', () => {
        const fixture = createFixture(mvpTree, {
            configurationMode: false,
            toggleableIds: new Set(['lecture']),
        });
        let toggled: string | undefined;
        fixture.componentInstance.toggleSelection.subscribe((id: string) => {
            toggled = id;
        });

        (nodeFor(fixture, 'lecture') as HTMLElement).dispatchEvent(new Event('click'));
        expect(toggled).toBeUndefined();
    });

    it('keeps the toggle badge expand-only in configuration mode', () => {
        const fixture = createFixture(mvpTree, {
            expandedIds: new Set(['artemis']),
            configurationMode: true,
            toggleableIds: new Set(['lecture']),
        });
        let expanded: string | undefined;
        let toggled: string | undefined;
        fixture.componentInstance.toggleExpand.subscribe((id: string) => {
            expanded = id;
        });
        fixture.componentInstance.toggleSelection.subscribe((id: string) => {
            toggled = id;
        });

        const event = new MouseEvent('click', { bubbles: true, cancelable: true });
        toggleFor(fixture, 'teaching-and-content').dispatchEvent(event);

        expect(expanded).toBe('teaching-and-content');
        expect(toggled).toBeUndefined();
    });
});

function parseTranslate(value: string): [number, number] {
    const match = value.match(/translate\(([-0-9.]+),\s*([-0-9.]+)\)/);
    if (!match) {
        return [0, 0];
    }
    return [Number(match[1]), Number(match[2])];
}
