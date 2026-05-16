import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { buildMvpFeatureModelResponse } from '../core/feature-model.test-fixtures';
import { FeatureTreeNode } from '../core/feature-model.types';
import { FeatureModelDiagramComponent } from './feature-model-diagram.component';

function createFixture(
    tree: FeatureTreeNode,
    matchedIds: ReadonlySet<string> = new Set(),
    selectedId: string | undefined = undefined,
): ComponentFixture<FeatureModelDiagramComponent> {
    TestBed.configureTestingModule({ imports: [FeatureModelDiagramComponent] });
    const fixture = TestBed.createComponent(FeatureModelDiagramComponent);
    fixture.componentRef.setInput('tree', tree);
    fixture.componentRef.setInput('matchedIds', matchedIds);
    fixture.componentRef.setInput('selectedId', selectedId);
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

describe('FeatureModelDiagramComponent', () => {
    let mvpTree: FeatureTreeNode;

    beforeEach(() => {
        mvpTree = buildMvpFeatureModelResponse().tree;
    });

    it('renders one group per feature in the 24-node MVP tree', () => {
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
        const fixture = createFixture(mvpTree, new Set(), 'programming');
        const selected = root(fixture).querySelector('.diagram-node--selected');
        expect(selected?.getAttribute('data-feature-id')).toBe('programming');
        expect(selected?.getAttribute('aria-selected')).toBe('true');
    });

    it('applies the match modifier to nodes in matchedIds', () => {
        const fixture = createFixture(mvpTree, new Set(['lecture', 'iris']));
        const matches = root(fixture).querySelectorAll('.diagram-node--match');
        const matchedIds = Array.from(matches).map((node) => node.getAttribute('data-feature-id'));
        expect(matchedIds).toEqual(expect.arrayContaining(['lecture', 'iris']));
        expect(matchedIds).toHaveLength(2);
    });

    it('renders one link per non-root node', () => {
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
});
