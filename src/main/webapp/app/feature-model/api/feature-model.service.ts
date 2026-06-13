import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { FeatureModelResponse } from '../core/feature-model.types';
import { GuidedWorkflow } from '../core/guided-workflow.types';
import { SnapshotSummary } from '../core/snapshot.types';

const FEATURE_MODEL_RESOURCE_URL = '/api/feature-model';
const GUIDED_WORKFLOW_RESOURCE_URL = '/api/feature-model/guided-workflow';
const SNAPSHOTS_RESOURCE_URL = '/api/feature-model/snapshots';

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

    /**
     * Issues `GET /api/feature-model/guided-workflow` and returns the parsed workflow used by
     * the guided Configurator.
     *
     * @returns Observable that emits the guided workflow response once and completes.
     */
    loadGuidedWorkflow(): Observable<GuidedWorkflow> {
        return this.http.get<GuidedWorkflow>(GUIDED_WORKFLOW_RESOURCE_URL);
    }

    /**
     * Issues `GET /api/feature-model/snapshots` and returns the imported local snapshots and their
     * metadata for the advanced Explorer view.
     *
     * @returns Observable that emits the imported snapshot summaries once and completes.
     */
    loadSnapshots(): Observable<SnapshotSummary[]> {
        return this.http.get<SnapshotSummary[]>(SNAPSHOTS_RESOURCE_URL);
    }
}
