import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ValidationRequest, ValidationResult } from '../core/feature-model.types';
import { FeatureModelValidationService } from './feature-model-validation.service';

function validResult(): ValidationResult {
    return {
        valid: true,
        normalizedSelection: ['course-workflow', 'communication'],
        violations: [],
        warnings: [],
    };
}

function invalidResult(): ValidationResult {
    return {
        valid: false,
        normalizedSelection: ['course-workflow'],
        violations: [
            {
                code: 'MANDATORY_FEATURE_MISSING',
                message: 'Programming is mandatory under Exercise System.',
                featureIds: ['programming'],
                relation: { parentId: 'exercise-system', childId: 'programming' },
                suggestion: 'Enable Programming.',
            },
        ],
        warnings: [],
    };
}

describe('FeatureModelValidationService', () => {
    let service: FeatureModelValidationService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(FeatureModelValidationService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('issues a POST to /api/feature-model/validate with the submitted ids', () => {
        const selected = new Set(['course-workflow', 'communication']);
        let received: ValidationResult | undefined;

        service.validateSelection(selected).subscribe((value) => {
            received = value;
        });

        const request = httpMock.expectOne('/api/feature-model/validate');
        expect(request.request.method).toBe('POST');
        const body = request.request.body as ValidationRequest;
        expect(body.selectedFeatureIds).toEqual(['course-workflow', 'communication']);

        const response = validResult();
        request.flush(response);
        expect(received).toEqual(response);
    });

    it('accepts a readonly string array and posts the same order', () => {
        const ordered: readonly string[] = ['communication', 'course-workflow'];
        service.validateSelection(ordered).subscribe();

        const request = httpMock.expectOne('/api/feature-model/validate');
        const body = request.request.body as ValidationRequest;
        expect(body.selectedFeatureIds).toEqual(['communication', 'course-workflow']);
        request.flush(validResult());
    });

    it('surfaces an invalid result with violations and suggestions', () => {
        let received: ValidationResult | undefined;
        service.validateSelection(new Set<string>()).subscribe((value) => {
            received = value;
        });

        const request = httpMock.expectOne('/api/feature-model/validate');
        const response = invalidResult();
        request.flush(response);

        expect(received?.valid).toBe(false);
        expect(received?.violations).toHaveLength(1);
        expect(received?.violations[0].code).toBe('MANDATORY_FEATURE_MISSING');
        expect(received?.violations[0].featureIds).toEqual(['programming']);
        expect(received?.violations[0].relation?.parentId).toBe('exercise-system');
        expect(received?.violations[0].suggestion).toBe('Enable Programming.');
    });

    it('surfaces HTTP errors to subscribers', () => {
        let observedError: HttpErrorResponse | undefined;

        service.validateSelection(new Set(['course-workflow'])).subscribe({
            next: () => {
                throw new Error('Expected error path');
            },
            error: (error: HttpErrorResponse) => {
                observedError = error;
            },
        });

        const request = httpMock.expectOne('/api/feature-model/validate');
        request.flush('Internal error', { status: 500, statusText: 'Server Error' });
        expect(observedError?.status).toBe(500);
    });

    it('serializes an empty selection as an empty array body', () => {
        service.validateSelection(new Set<string>()).subscribe();

        const request = httpMock.expectOne('/api/feature-model/validate');
        const body = request.request.body as ValidationRequest;
        expect(body.selectedFeatureIds).toEqual([]);
        request.flush(validResult());
    });
});
