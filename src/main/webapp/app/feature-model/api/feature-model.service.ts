import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { FeatureModelResponse } from '../core/feature-model.types';

const FEATURE_MODEL_RESOURCE_URL = '/api/feature-model';

@Injectable({ providedIn: 'root' })
export class FeatureModelService {
    private readonly http = inject(HttpClient);

    /**
     * Issues `GET /api/feature-model` and returns the parsed response as a cold observable.
     * HTTP errors are not swallowed; subscribers receive the original `HttpErrorResponse`.
     *
     * @returns Observable that emits the feature-model response once and completes.
     */
    loadFeatureModel(): Observable<FeatureModelResponse> {
        return this.http.get<FeatureModelResponse>(FEATURE_MODEL_RESOURCE_URL);
    }
}
