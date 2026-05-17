import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { buildMvpFeatureModelResponse } from '../core/feature-model.test-fixtures';
import { FeatureModelResponse } from '../core/feature-model.types';
import { FeatureModelConfiguratorComponent } from './feature-model-configurator.component';

function setup(): {
    fixture: ComponentFixture<FeatureModelConfiguratorComponent>;
    httpMock: HttpTestingController;
} {
    TestBed.configureTestingModule({
        imports: [FeatureModelConfiguratorComponent],
        providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(FeatureModelConfiguratorComponent);
    const httpMock = TestBed.inject(HttpTestingController);
    return { fixture, httpMock };
}

function expectModelRequest(httpMock: HttpTestingController): { flush: (response: FeatureModelResponse) => void } {
    const request = httpMock.expectOne('/api/feature-model');
    expect(request.request.method).toBe('GET');
    return {
        flush: (response: FeatureModelResponse) => request.flush(response),
    };
}

function rootEl(fixture: ComponentFixture<FeatureModelConfiguratorComponent>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
}

describe('FeatureModelConfiguratorComponent', () => {
    let fixture: ComponentFixture<FeatureModelConfiguratorComponent>;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        ({ fixture, httpMock } = setup());
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('renders the loading state before the model resolves', () => {
        fixture.detectChanges();
        const loading = rootEl(fixture).querySelector('[data-testid="loading-state"]');
        expect(loading).not.toBeNull();
        expect(loading?.textContent).toContain('Loading feature model');
        httpMock.expectOne('/api/feature-model').flush(buildMvpFeatureModelResponse());
        fixture.detectChanges();
    });

    it('renders the model name and stats after the response loads', () => {
        fixture.detectChanges();
        expectModelRequest(httpMock).flush(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        const heading = rootEl(fixture).querySelector('h1');
        expect(heading?.textContent).toBe('Artemis Functional Feature Tree');
        const selectedCount = rootEl(fixture).querySelector('[data-testid="selected-count"]');
        expect(selectedCount?.textContent?.trim()).toBe('13');
    });

    it('renders the tree diagram (and only the diagram) after the response loads', () => {
        fixture.detectChanges();
        expectModelRequest(httpMock).flush(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        const diagram = rootEl(fixture).querySelector('fm-feature-model-diagram');
        expect(diagram).not.toBeNull();
        expect(rootEl(fixture).querySelector('fm-feature-model-tree-node')).toBeNull();
        expect(rootEl(fixture).querySelector('[data-testid="view-list"]')).toBeNull();
        expect(rootEl(fixture).querySelector('[data-testid="view-diagram"]')).toBeNull();
    });

    it('initializes selectedFeatureIds from defaultSelectedFeatureIds', () => {
        fixture.detectChanges();
        const response = buildMvpFeatureModelResponse();
        expectModelRequest(httpMock).flush(response);
        fixture.detectChanges();

        const selected = fixture.componentInstance.selectedFeatureIds();
        expect(selected.size).toBe(response.defaultSelectedFeatureIds.length);
        for (const id of response.defaultSelectedFeatureIds) {
            expect(selected.has(id)).toBe(true);
        }
    });

    it('focuses the root feature on load', () => {
        fixture.detectChanges();
        expectModelRequest(httpMock).flush(buildMvpFeatureModelResponse());
        fixture.detectChanges();

        expect(fixture.componentInstance.selectedFeatureId()).toBe('artemis');
        const detailsName = rootEl(fixture).querySelector('[data-testid="details-name"]');
        expect(detailsName?.textContent).toBe('Artemis');
    });

    it('renders the error panel when the model service fails', () => {
        fixture.detectChanges();
        const request = httpMock.expectOne('/api/feature-model');
        request.flush('Boom', { status: 500, statusText: 'Server Error' });
        fixture.detectChanges();

        const error = rootEl(fixture).querySelector('[data-testid="error-state"]');
        expect(error).not.toBeNull();
        expect(rootEl(fixture).querySelector('[data-testid="loading-state"]')).toBeNull();
    });
});
