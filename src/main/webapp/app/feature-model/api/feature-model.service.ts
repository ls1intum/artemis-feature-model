import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ArtifactGenerationRequest, ArtifactGenerationResult } from '../core/artifact-generation.types';
import { WorkflowAvailability } from '../core/deployment-profile.types';
import { FeatureModelResponse } from '../core/feature-model.types';
import { GuidedWorkflow } from '../core/guided-workflow.types';
import { SnapshotSummary } from '../core/snapshot.types';

const FEATURE_MODEL_RESOURCE_URL = '/api/feature-model';
const GUIDED_WORKFLOW_RESOURCE_URL = '/api/feature-model/guided-workflow';
const PROFILE_AVAILABILITY_RESOURCE_URL = '/api/feature-model/profile-availability';
const SNAPSHOTS_RESOURCE_URL = '/api/feature-model/snapshots';
const ARTIFACTS_PREVIEW_RESOURCE_URL = '/api/feature-model/artifacts/preview';
const ARTIFACTS_DOWNLOAD_RESOURCE_URL = '/api/feature-model/artifacts/download';

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
     * Issues `GET /api/feature-model/profile-availability` and returns profile-aware availability of the active
     * guided workflow and feature model. Resolves against the requested profile, or the default profile when no id
     * is given.
     *
     * @param profileId Optional profile id to resolve availability against.
     * @returns Observable that emits the workflow availability once and completes.
     */
    loadWorkflowAvailability(profileId?: string): Observable<WorkflowAvailability> {
        const options = profileId ? { params: { profileId } } : {};
        return this.http.get<WorkflowAvailability>(PROFILE_AVAILABILITY_RESOURCE_URL, options);
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

    /**
     * Issues `POST /api/feature-model/artifacts/preview` and returns the generated artifact files and report for
     * the given selection. Secret values appear only as `${VARIABLE}` placeholders.
     *
     * @param request Artifact generation request with the selected feature ids.
     * @returns Observable that emits the artifact generation result once and completes.
     */
    previewArtifacts(request: ArtifactGenerationRequest): Observable<ArtifactGenerationResult> {
        return this.http.post<ArtifactGenerationResult>(ARTIFACTS_PREVIEW_RESOURCE_URL, request);
    }

    /**
     * Issues `POST /api/feature-model/artifacts/download` and returns the artifact ZIP package as a binary blob.
     *
     * @param request Artifact generation request with the selected feature ids.
     * @returns Observable that emits the ZIP package blob once and completes.
     */
    downloadArtifacts(request: ArtifactGenerationRequest): Observable<Blob> {
        return this.http.post(ARTIFACTS_DOWNLOAD_RESOURCE_URL, request, { responseType: 'blob' });
    }
}
