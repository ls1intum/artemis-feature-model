import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { FeatureModelService } from '../api/feature-model.service';
import { buildFeatureModelResponseWithSubFeatures, buildMvpFeatureModelResponse } from '../core/feature-model.test-fixtures';
import { FeatureModelResponse } from '../core/feature-model.types';
import { FeatureModelExplorerComponent } from './feature-model-explorer.component';

function createServiceStub(): { service: { loadFeatureModel: ReturnType<typeof vi.fn>; loadSnapshots: ReturnType<typeof vi.fn> }; subject: Subject<FeatureModelResponse> } {
    const subject = new Subject<FeatureModelResponse>();
    const service = {
        loadFeatureModel: vi.fn(() => subject.asObservable()),
        loadSnapshots: vi.fn(() => of([])),
    };
    return { service, subject };
}

function configureComponent(serviceStub: { loadFeatureModel: ReturnType<typeof vi.fn>; loadSnapshots: ReturnType<typeof vi.fn> }): ComponentFixture<FeatureModelExplorerComponent> {
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

function expandSourceMetadata(fixture: ComponentFixture<FeatureModelExplorerComponent>): void {
    const toggle = rootElement(fixture).querySelector('[data-testid="details-source-toggle"]');
    if (!toggle) {
        throw new Error('Source metadata disclosure not found.');
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

        const stats = Array.from(rootElement(fixture).querySelectorAll('.explorer-stat')).map((stat) => ({
            value: stat.querySelector('.explorer-stat__value')?.textContent?.trim(),
            label: stat.querySelector('.explorer-stat__label')?.textContent?.trim(),
        }));
        expect(stats).toEqual([
            { value: '18', label: 'Features' },
            { value: '23', label: 'Relations' },
            { value: '0', label: 'Constraints' },
            { value: '13', label: 'Default on' },
        ]);
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
        expandSourceMetadata(fixture);

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

    it('filters by feature id and finds fileupload', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        setSearch(fixture, 'fileupload');

        const ids = getRenderedFeatureIds(fixture);
        expect(ids).toContain('fileupload');
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
            loadSnapshots: vi.fn(() => of([])),
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

    it('renders the diagram view with only the initially expanded branch visible', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        const diagramToggle = rootElement(fixture).querySelector('[data-testid="view-diagram"]') as HTMLButtonElement | null;
        diagramToggle?.click();
        fixture.detectChanges();

        const diagram = rootElement(fixture).querySelector('[data-testid="diagram"]');
        expect(diagram).not.toBeNull();
        const visibleIds = Array.from(rootElement(fixture).querySelectorAll('.diagram-node')).map((node) =>
            node.getAttribute('data-feature-id'),
        );
        expect(visibleIds).toContain('artemis');
        expect(visibleIds).toContain('teaching-and-content');
        expect(visibleIds).not.toContain('lecture');
        expect(visibleIds).toHaveLength(6);
        expect(rootElement(fixture).querySelector('[data-testid="list-view"]')).toBeNull();
    });

    it('renders all 24 nodes in diagram view after Expand all', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        const expandAll = rootElement(fixture).querySelector('[data-testid="expand-all"]') as HTMLButtonElement | null;
        expandAll?.click();
        fixture.detectChanges();

        const diagramToggle = rootElement(fixture).querySelector('[data-testid="view-diagram"]') as HTMLButtonElement | null;
        diagramToggle?.click();
        fixture.detectChanges();

        expect(rootElement(fixture).querySelectorAll('.diagram-node')).toHaveLength(24);
    });

    it('keeps Expand all and Collapse all controls available in diagram view', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        const diagramToggle = rootElement(fixture).querySelector('[data-testid="view-diagram"]') as HTMLButtonElement | null;
        diagramToggle?.click();
        fixture.detectChanges();

        expect(rootElement(fixture).querySelector('[data-testid="expand-all"]')).not.toBeNull();
        expect(rootElement(fixture).querySelector('[data-testid="collapse-all"]')).not.toBeNull();
    });

    it('expands a subtree when the diagram toggle badge is clicked', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        const diagramToggle = rootElement(fixture).querySelector('[data-testid="view-diagram"]') as HTMLButtonElement | null;
        diagramToggle?.click();
        fixture.detectChanges();

        const teachingToggle = rootElement(fixture).querySelector(
            '.diagram-toggle[data-feature-id="teaching-and-content"]',
        ) as Element | null;
        teachingToggle?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
        fixture.detectChanges();

        const visibleIds = Array.from(rootElement(fixture).querySelectorAll('.diagram-node')).map((node) =>
            node.getAttribute('data-feature-id'),
        );
        expect(visibleIds).toContain('lecture');
        expect(visibleIds).toContain('communication');
    });

    it('keeps selection and details consistent across the view toggle', () => {
        fixture.detectChanges();
        stub.subject.next(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        const expandAll = rootElement(fixture).querySelector('[data-testid="expand-all"]') as HTMLButtonElement | null;
        expandAll?.click();
        fixture.detectChanges();

        const diagramToggle = rootElement(fixture).querySelector('[data-testid="view-diagram"]') as HTMLButtonElement | null;
        diagramToggle?.click();
        fixture.detectChanges();

        const lectureNode = rootElement(fixture).querySelector('.diagram-node[data-feature-id="lecture"]') as HTMLElement | null;
        lectureNode?.dispatchEvent(new Event('click'));
        fixture.detectChanges();

        expect(rootElement(fixture).querySelector('[data-testid="details-name"]')?.textContent).toContain('Lecture');
        expandSourceMetadata(fixture);
        expect(rootElement(fixture).querySelector('[data-testid="details-config-key"]')?.textContent).toContain('artemis.lecture.enabled');

        const listToggle = rootElement(fixture).querySelector('[data-testid="view-list"]') as HTMLButtonElement | null;
        listToggle?.click();
        fixture.detectChanges();

        expect(rootElement(fixture).querySelector('[data-testid="details-name"]')?.textContent).toContain('Lecture');
    });

    describe('with FeatureUsage sub-features', () => {
        function load(): void {
            fixture.detectChanges();
            stub.subject.next(buildFeatureModelResponseWithSubFeatures());
            fixture.detectChanges();
        }

        function statTiles(): { value: string | undefined; label: string | undefined }[] {
            return Array.from(rootElement(fixture).querySelectorAll('.explorer-stat')).map((stat) => ({
                value: stat.querySelector('.explorer-stat__value')?.textContent?.trim(),
                label: stat.querySelector('.explorer-stat__label')?.textContent?.trim(),
            }));
        }

        it('counts only selectable features and shows a Sub-features tile', () => {
            load();
            expect(statTiles()).toEqual([
                { value: '19', label: 'Features' },
                { value: '3', label: 'Sub-features' },
                { value: '27', label: 'Relations' },
                { value: '0', label: 'Constraints' },
                { value: '14', label: 'Default on' },
            ]);
        });

        it('omits the Sub-features tile for a model without sub-features', () => {
            fixture.detectChanges();
            stub.subject.next(buildMvpFeatureModelResponse());
            fixture.detectChanges();
            expect(rootElement(fixture).querySelector('[data-testid="stat-sub-features"]')).toBeNull();
        });

        it('adds a hollow Sub-feature entry to the kind legend', () => {
            load();
            const entries = Array.from(rootElement(fixture).querySelectorAll('.kind-legend > span')).filter((span) => span.querySelector('.fm-kind-dot'));
            const subFeature = entries.find((span) => span.textContent?.trim() === 'Sub-feature');
            expect(subFeature).toBeDefined();
            const dot = subFeature?.querySelector('.fm-kind-dot');
            expect(dot?.classList.contains('fm-kind-dot--sub-feature')).toBe(true);
            expect(dot?.classList.contains('fm-kind-dot--hollow')).toBe(true);
        });

        it('renders sub-feature rows below their owner with the area chip and a hollow dot', () => {
            load();
            clickToggle(fixture, 'teaching-and-content');
            clickToggle(fixture, 'lecture');

            const row = rootElement(fixture).querySelector('.tree-node[data-feature-id="lecture/ai/transcription"] .tree-row');
            expect(row).not.toBeNull();
            expect(row?.classList.contains('tree-row--sub-feature')).toBe(true);
            expect(row?.querySelector('[data-testid="tree-area"]')?.textContent?.trim()).toBe('ai');
            expect(row?.querySelector('.fm-kind-dot')?.classList.contains('fm-kind-dot--hollow')).toBe(true);
            expect(row?.querySelector('.tree-toggle')).toBeNull();
            expect(getRenderedFeatureIds(fixture)).toContain('lecture/authoring/lectures');
        });

        it('lists the sub-features of the selected member grouped by area and selects one on click', () => {
            load();
            clickToggle(fixture, 'teaching-and-content');
            clickRow(fixture, 'lecture');

            const section = rootElement(fixture).querySelector('[data-testid="details-sub-features"]');
            expect(section).not.toBeNull();
            const areas = Array.from(section?.querySelectorAll('[data-testid="details-sub-feature-area"]') ?? []).map((area) => area.textContent?.trim());
            expect(areas).toEqual(['ai', 'authoring']);

            const link = section?.querySelector('.sub-feature-link[data-feature-id="lecture/authoring/lectures"]') as HTMLButtonElement | null;
            link?.click();
            fixture.detectChanges();
            expect(rootElement(fixture).querySelector('[data-testid="details-id"]')?.textContent).toContain('lecture/authoring/lectures');
        });

        it('shows area, label, owner, and evidence for a selected sub-feature', () => {
            load();
            clickToggle(fixture, 'teaching-and-content');
            clickToggle(fixture, 'lecture');
            clickRow(fixture, 'lecture/ai/transcription');

            const details = rootElement(fixture);
            expect(details.querySelector('[data-testid="details-name"]')?.textContent).toContain('Transcription');
            expect(details.querySelector('[data-testid="details-usage-area"]')?.textContent?.trim()).toBe('ai');
            expect(details.querySelector('[data-testid="details-usage-label"]')?.textContent?.trim()).toBe('ai/transcription');
            expect(details.querySelector('[data-testid="details-parent-id"]')?.textContent?.trim()).toBe('lecture');
            expect(details.querySelector('[data-testid="details-evidence"]')?.textContent).toContain('LectureTranscriptionResource.java:25');
            expect(details.querySelector('[data-testid="details-sub-features"]')).toBeNull();

            expandSourceMetadata(fixture);
            expect(details.querySelector('[data-testid="details-source"]')?.textContent).toContain('LectureEnabled');
        });

        it('finds a sub-feature through its usage label in the search', () => {
            load();
            setSearch(fixture, 'vcs/');

            expect(rootElement(fixture).querySelector('[data-testid="match-count"]')?.textContent).toContain('1 match');
            expect(getRenderedFeatureIds(fixture)).toEqual(['artemis', 'localvc', 'localvc/vcs/repositories']);
            expect(rootElement(fixture).querySelector('.tree-node[data-feature-id="localvc/vcs/repositories"] .tree-row--match')).not.toBeNull();
        });

        it('lays out no sub-feature node in the diagram and badges the owners instead', () => {
            load();
            (rootElement(fixture).querySelector('[data-testid="expand-all"]') as HTMLButtonElement | null)?.click();
            fixture.detectChanges();
            (rootElement(fixture).querySelector('[data-testid="view-diagram"]') as HTMLButtonElement | null)?.click();
            fixture.detectChanges();

            const nodeIds = Array.from(rootElement(fixture).querySelectorAll('.diagram-node')).map((node) => node.getAttribute('data-feature-id'));
            expect(nodeIds).toHaveLength(25);
            expect(nodeIds).not.toContain('lecture/ai/transcription');
            const badges = Array.from(rootElement(fixture).querySelectorAll('[data-testid="sub-feature-badge"]'))
                .map((badge) => [badge.getAttribute('data-feature-id') ?? '', badge.querySelector('.diagram-subfeatures__label')?.textContent?.trim() ?? ''])
                .sort((left, right) => left[0].localeCompare(right[0]));
            expect(badges).toEqual([
                ['lecture', '2 sub'],
                ['localvc', '1 sub'],
            ]);
        });
    });
});
