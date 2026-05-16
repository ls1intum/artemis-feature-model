import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { FeatureModelResponse } from '../core/feature-model.types';
import { FeatureModelService } from './feature-model.service';

function makeResponse(): FeatureModelResponse {
    return {
        model: { id: 'artemis-functional-feature-tree', name: 'Artemis Functional Feature Tree', version: '0.1.0' },
        features: [],
        relations: [],
        constraints: [],
        tree: {
            feature: {
                id: 'artemis',
                name: 'Artemis',
                kind: 'root',
                selectable: false,
                description: null,
                defaultState: 'not_applicable',
                source: null,
            },
            incomingRelation: null,
            children: [],
        },
        defaultSelectedFeatureIds: [],
        warnings: [],
    };
}

describe('FeatureModelService', () => {
    let service: FeatureModelService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(FeatureModelService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('issues a GET request to /api/feature-model', () => {
        const response = makeResponse();
        let received: FeatureModelResponse | undefined;

        service.loadFeatureModel().subscribe((value) => {
            received = value;
        });

        const request = httpMock.expectOne('/api/feature-model');
        expect(request.request.method).toBe('GET');
        request.flush(response);
        expect(received).toEqual(response);
    });

    it('surfaces HTTP errors to subscribers', () => {
        let observedError: HttpErrorResponse | undefined;

        service.loadFeatureModel().subscribe({
            next: () => {
                throw new Error('Expected error path');
            },
            error: (error: HttpErrorResponse) => {
                observedError = error;
            },
        });

        const request = httpMock.expectOne('/api/feature-model');
        request.flush('Internal error', { status: 500, statusText: 'Server Error' });
        expect(observedError?.status).toBe(500);
    });
});
