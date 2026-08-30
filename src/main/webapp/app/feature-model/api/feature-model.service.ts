import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ArtifactGenerationRequest, DeploymentPackagePublishResponse, DeploymentPackagePublishTarget } from '../core/artifact-generation.types';
import { WorkflowAvailability } from '../core/deployment-profile.types';
import { FeatureModelResponse } from '../core/feature-model.types';
import { GuidedWorkflow } from '../core/guided-workflow.types';
import { SnapshotSummary } from '../core/snapshot.types';

const FEATURE_MODEL_RESOURCE_URL = '/api/feature-model';
const GUIDED_WORKFLOW_RESOURCE_URL = '/api/feature-model/guided-workflow';
const PROFILE_AVAILABILITY_RESOURCE_URL = '/api/feature-model/profile-availability';
const SNAPSHOTS_RESOURCE_URL = '/api/feature-model/snapshots';
const ARTIFACTS_DOWNLOAD_RESOURCE_URL = '/api/feature-model/artifacts/download';
const DEPLOYMENT_PACKAGE_DOWNLOAD_RESOURCE_URL = '/api/feature-model/deployment-package/download';
const DEPLOYMENT_PACKAGE_PUBLISH_RESOURCE_URL = '/api/feature-model/deployment-package/publish';
const DEPLOYMENT_PACKAGE_PUBLISH_TARGET_RESOURCE_URL = '/api/feature-model/deployment-package/publish-target';

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
     * Issues `POST /api/feature-model/artifacts/download` and returns the artifact ZIP package as a binary blob.
     *
     * @param request Artifact generation request with the selected feature ids.
     * @returns Observable that emits the ZIP package blob once and completes.
     */
    downloadArtifacts(request: ArtifactGenerationRequest): Observable<Blob> {
        return this.http.post(ARTIFACTS_DOWNLOAD_RESOURCE_URL, request, { responseType: 'blob' });
    }

    /**
     * Issues `POST /api/feature-model/deployment-package/download` and returns the local runtime deployment package
     * ZIP as a binary blob. The package reuses the Level 1 artifacts and adds local-repo runtime templates and helper
     * scripts. Secret values appear only as `${VARIABLE}` placeholders; raw secrets are never transferred.
     *
     * @param request Artifact generation request with the selected feature ids.
     * @returns Observable that emits the deployment package ZIP blob once and completes.
     */
    downloadDeploymentPackage(request: ArtifactGenerationRequest): Observable<Blob> {
        return this.http.post(DEPLOYMENT_PACKAGE_DOWNLOAD_RESOURCE_URL, request, { responseType: 'blob' });
    }

    /**
     * Issues `POST /api/feature-model/deployment-package/publish` and returns the commit that now carries the
     * remote-ansible package in the deployment repository. The package is generated through the same path as the
     * download, so the published tree and the downloaded ZIP are byte-identical for the same request.
     *
     * @param request Artifact generation request with the remote-ansible deployment mode and a target name.
     * @returns Observable that emits the publish response once and completes.
     */
    publishDeploymentPackage(request: ArtifactGenerationRequest): Observable<DeploymentPackagePublishResponse> {
        return this.http.post<DeploymentPackagePublishResponse>(DEPLOYMENT_PACKAGE_PUBLISH_RESOURCE_URL, request);
    }

    /**
     * Issues `GET /api/feature-model/deployment-package/publish-target` and returns where a publish would go, so the
     * review page can show the destination and hide the publish action on an unconfigured instance.
     *
     * @returns Observable that emits the publish target once and completes.
     */
    loadDeploymentPackagePublishTarget(): Observable<DeploymentPackagePublishTarget> {
        return this.http.get<DeploymentPackagePublishTarget>(DEPLOYMENT_PACKAGE_PUBLISH_TARGET_RESOURCE_URL);
    }
}
