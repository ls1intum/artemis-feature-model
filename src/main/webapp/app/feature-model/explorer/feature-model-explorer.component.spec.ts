import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { FeatureModelService } from '../api/feature-model.service';
import { buildMvpFeatureModelResponse } from '../core/feature-model.test-fixtures';
import { FeatureModelResponse } from '../core/feature-model.types';
import { FeatureModelExplorerComponent } from './feature-model-explorer.component';

function createServiceStub(): { service: { loadFeatureModel: ReturnType<typeof vi.fn> }; subject: Subject<FeatureModelResponse> } {
    const subject = new Subject<FeatureModelResponse>();
    const service = {
        loadFeatureModel: vi.fn(() => subject.asObservable()),
    };
    return { service, subject };
}

function configureComponent(serviceStub: { loadFeatureModel: ReturnType<typeof vi.fn> }): ComponentFixture<FeatureModelExplorerComponent> {
    TestBed.configureTestingModule({
        imports: [FeatureModelExplorerComponent],
        providers: [{ provide: FeatureModelService, useValue: serviceStub }],
    });
    return TestBed.createComponent(FeatureModelExplorerComponent);
}

function rootElement(fixture: ComponentFixture<FeatureModelExplorerComponent>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
}

function getRenderedFeatureIds(fixture: ComponentFixture<FeatureModelExplorerComponent>): string[] {
    const rows = rootElement(fixture).querySelectorAll('.tree-node');
    return Array.from(rows).map((row) => (row as HTMLElement).dataset['featureId'] ?? '');
}

function clickRow(fixture: ComponentFixture<FeatureModelExplorerComponent>, featureId: string): void {
    const row = rootElement(fixture).querySelector(`.tree-node[data-feature-id="${featureId}"] .tree-row`);
    if (!row) {
        throw new Error(`No tree row for feature ${featureId}`);
    }
    (row as HTMLElement).click();
    fixture.detectChanges();
}

function clickToggle(fixture: ComponentFixture<FeatureModelExplorerComponent>, featureId: string): void {
    const toggle = rootElement(fixture).querySelector(`.tree-node[data-feature-id="${featureId}"] .tree-toggle`);
    if (!toggle) {
        throw new Error(`No expand toggle for feature ${featureId}`);
    }
    (toggle as HTMLElement).click();
    fixture.detectChanges();
}

function setSearch(fixture: ComponentFixture<FeatureModelExplorerComponent>, value: string): void {
    const input = rootElement(fixture).querySelector('[data-testid="search-input"]') as HTMLInputElement | null;
    if (!input) {
        throw new Error('Search input not found.');
    }
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
}

describe('FeatureModelExplorerComponent', () => {
    let stub: ReturnType<typeof createServiceStub>;
    let fixture: ComponentFixture<FeatureModelExplorerComponent>;

    beforeEach(() => {
        stub = createServiceStub();
        fixture = configureComponent(stub.service);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('shows the loading state before the API responds', () => {
        fixture.detectChanges();
        const loading = fixture.nativeElement.querySelector('[data-testid="loading-state"]');
        expect(loading).not.toBeNull();
        expect(loading?.textContent).toContain('Loading feature model');
    });

    it('renders the model metadata and feature counts after loading', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        const heading = rootElement(fixture).querySelector('.explorer-title h1');
        expect(heading?.textContent).toContain('Artemis Functional Feature Tree');

        const stats = rootElement(fixture).querySelector('.explorer-stats')?.textContent ?? '';
        expect(stats).toContain('24 features');
        expect(stats).toContain('23 relations');
        expect(stats).toContain('0 constraints');
    });

    it('renders the full 24-node tree when every branch is expanded', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        const expandAll = rootElement(fixture).querySelector('[data-testid="expand-all"]') as HTMLButtonElement | null;
        expandAll?.click();
        fixture.detectChanges();

        expect(getRenderedFeatureIds(fixture)).toHaveLength(24);
        expect(getRenderedFeatureIds(fixture)).toContain('artemis');
        expect(getRenderedFeatureIds(fixture)).toContain('teaching-and-content');
        expect(getRenderedFeatureIds(fixture)).toContain('lecture');
    });

    it('hides descendants after Collapse all and shows only the root', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        const collapseAll = rootElement(fixture).querySelector('[data-testid="collapse-all"]') as HTMLButtonElement | null;
        collapseAll?.click();
        fixture.detectChanges();

        expect(getRenderedFeatureIds(fixture)).toEqual(['artemis']);
    });

    it('toggles a single branch when its expand button is clicked', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        expect(getRenderedFeatureIds(fixture)).not.toContain('lecture');
        clickToggle(fixture, 'teaching-and-content');
        expect(getRenderedFeatureIds(fixture)).toContain('lecture');
        clickToggle(fixture, 'teaching-and-content');
        expect(getRenderedFeatureIds(fixture)).not.toContain('lecture');
    });

    it('updates the details panel when the user selects Lecture', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        clickToggle(fixture, 'teaching-and-content');
        clickRow(fixture, 'lecture');

        const name = fixture.nativeElement.querySelector('[data-testid="details-name"]');
        const id = fixture.nativeElement.querySelector('[data-testid="details-id"]');
        const configKey = fixture.nativeElement.querySelector('[data-testid="details-config-key"]');

        expect(name?.textContent).toContain('Lecture');
        expect(id?.textContent).toContain('lecture');
        expect(configKey?.textContent).toContain('artemis.lecture.enabled');
    });

    it('filters the tree by feature name (case-insensitive)', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        setSearch(fixture, 'lecture');

        const ids = getRenderedFeatureIds(fixture);
        expect(ids).toContain('lecture');
        expect(ids).toContain('teaching-and-content');
        expect(ids).toContain('artemis');
        expect(ids).not.toContain('exam');
        expect(ids).not.toContain('exercise-system');

        const matchCount = fixture.nativeElement.querySelector('[data-testid="match-count"]');
        expect(matchCount?.textContent).toContain('1 match');
    });

    it('filters by feature id and finds file-upload', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        setSearch(fixture, 'file-upload');

        const ids = getRenderedFeatureIds(fixture);
        expect(ids).toContain('file-upload');
        expect(ids).toContain('exercise-system');
        expect(ids).not.toContain('lecture');
    });

    it('shows an empty-state message when nothing matches the search', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        setSearch(fixture, 'this-id-does-not-exist');

        const emptyState = fixture.nativeElement.querySelector('[data-testid="empty-tree"]');
        expect(emptyState).not.toBeNull();
        expect(getRenderedFeatureIds(fixture)).toEqual([]);
    });

    it('restores the unfiltered tree when the search is cleared', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        setSearch(fixture, 'lecture');
        expect(getRenderedFeatureIds(fixture)).not.toContain('exercise-system');

        setSearch(fixture, '');
        const ids = getRenderedFeatureIds(fixture);
        expect(ids).toContain('artemis');
        expect(ids).toContain('teaching-and-content');
        expect(ids).toContain('exercise-system');
    });

    it('renders model warnings when the server returns any', () => {
        fixture.detectChanges();
        const response = buildMvpFeatureModelResponse({
            warnings: [
                { code: 'UNSUPPORTED_EXPRESSION_CONSTRAINT', message: 'Constraint expression is unsupported.', featureIds: ['lecture'], constraintId: 'c1' },
            ],
        });
        stub.subject.next(response);
        fixture.detectChanges();

        const warnings = fixture.nativeElement.querySelector('[data-testid="warnings-panel"]');
        expect(warnings?.textContent).toContain('UNSUPPORTED_EXPRESSION_CONSTRAINT');
        expect(warnings?.textContent).toContain('Constraint expression is unsupported.');
    });

    it('hides the warnings panel when there are no warnings', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="warnings-panel"]')).toBeNull();
    });

    it('shows an error message when the API call fails', () => {
        const failingService = {
            loadFeatureModel: vi.fn(() => throwError(() => new Error('boom'))),
        };
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({
            imports: [FeatureModelExplorerComponent],
            providers: [{ provide: FeatureModelService, useValue: failingService }],
        });
        const failingFixture = TestBed.createComponent(FeatureModelExplorerComponent);
        failingFixture.detectChanges();

        const error = failingFixture.nativeElement.querySelector('[data-testid="error-state"]');
        expect(error?.textContent).toContain('boom');
    });

    it('starts in list view with the diagram view hidden', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        expect(rootElement(fixture).querySelector('[data-testid="list-view"]')).not.toBeNull();
        expect(rootElement(fixture).querySelector('[data-testid="diagram"]')).toBeNull();

        const listToggle = rootElement(fixture).querySelector('[data-testid="view-list"]');
        expect(listToggle?.getAttribute('aria-pressed')).toBe('true');
    });

    it('renders the diagram view when the user toggles to Diagram', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        const diagramToggle = rootElement(fixture).querySelector('[data-testid="view-diagram"]') as HTMLButtonElement | null;
        diagramToggle?.click();
        fixture.detectChanges();

        const diagram = rootElement(fixture).querySelector('[data-testid="diagram"]');
        expect(diagram).not.toBeNull();
        expect(rootElement(fixture).querySelectorAll('.diagram-node')).toHaveLength(24);
        expect(rootElement(fixture).querySelector('[data-testid="list-view"]')).toBeNull();
    });

    it('hides Expand all and Collapse all controls in diagram view', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        const diagramToggle = rootElement(fixture).querySelector('[data-testid="view-diagram"]') as HTMLButtonElement | null;
        diagramToggle?.click();
        fixture.detectChanges();

        expect(rootElement(fixture).querySelector('[data-testid="expand-all"]')).toBeNull();
        expect(rootElement(fixture).querySelector('[data-testid="collapse-all"]')).toBeNull();
    });

    it('keeps selection and details consistent across the view toggle', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        const diagramToggle = rootElement(fixture).querySelector('[data-testid="view-diagram"]') as HTMLButtonElement | null;
        diagramToggle?.click();
        fixture.detectChanges();

        const lectureNode = rootElement(fixture).querySelector('.diagram-node[data-feature-id="lecture"]') as HTMLElement | null;
        lectureNode?.dispatchEvent(new Event('click'));
        fixture.detectChanges();

        expect(rootElement(fixture).querySelector('[data-testid="details-name"]')?.textContent).toContain('Lecture');
        expect(rootElement(fixture).querySelector('[data-testid="details-config-key"]')?.textContent).toContain('artemis.lecture.enabled');

        const listToggle = rootElement(fixture).querySelector('[data-testid="view-list"]') as HTMLButtonElement | null;
        listToggle?.click();
        fixture.detectChanges();

        expect(rootElement(fixture).querySelector('[data-testid="details-name"]')?.textContent).toContain('Lecture');
    });
});
