import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ValidationRequest, ValidationResult } from '../core/feature-model.types';

const FEATURE_MODEL_VALIDATE_URL = '/api/feature-model/validate';

@Injectable({ providedIn: 'root' })
export class FeatureModelValidationService {
    private readonly http = inject(HttpClient);

    /**
     * Issues `POST /api/feature-model/validate` with the supplied selected feature ids and returns
     * the parsed validation result as a cold observable. HTTP errors are not swallowed; subscribers
     * receive the original `HttpErrorResponse`.
     *
     * @param selectedFeatureIds Selected feature ids to validate. Accepts any read-only iterable so
     *     the configurator can pass either a `ReadonlySet<string>` or `readonly string[]`.
     * @returns Observable that emits the validation result once and completes.
     */
    validateSelection(selectedFeatureIds: ReadonlySet<string> | readonly string[]): Observable<ValidationResult> {
        const body: ValidationRequest = { selectedFeatureIds: Array.from(selectedFeatureIds) };
        return this.http.post<ValidationResult>(FEATURE_MODEL_VALIDATE_URL, body);
    }
}
