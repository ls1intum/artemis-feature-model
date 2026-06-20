import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { FeatureModelResponse } from '../core/feature-model.types';
import { GuidedWorkflow } from '../core/guided-workflow.types';
import { SnapshotSummary } from '../core/snapshot.types';
import { FeatureModelService } from './feature-model.service';

function makeResponse(): FeatureModelResponse {
    return {
        model: {
            id: 'artemis-functional-feature-tree',
            name: 'Artemis Functional Feature Tree',
            version: '0.1.0',
            status: 'published',
            sourceCommitSha: null,
        },
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
                category: 'derived',
                visibleTo: [],
                configurableBy: [],
                requiresCapabilities: [],
                artifactMappings: [],
                extraction: null,
            },
            incomingRelation: null,
            children: [],
        },
        defaultSelectedFeatureIds: [],
        warnings: [],
    };
}

function makeGuidedWorkflow(): GuidedWorkflow {
    return {
        workflow: {
            id: 'artemis-guided-configuration',
            name: 'Artemis Guided Configuration Workflow',
            version: '0.1.0',
            featureModelId: 'artemis-functional-feature-tree',
            featureModelVersion: '0.1.0',
            defaultTemplateId: 'custom-configuration',
        },
        useCaseTemplates: [],
        steps: [],
        finalReviewGroups: [],
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

    it('issues a GET request to /api/feature-model/guided-workflow', () => {
        const workflow = makeGuidedWorkflow();
        let received: GuidedWorkflow | undefined;

        service.loadGuidedWorkflow().subscribe((value) => {
            received = value;
        });

        const request = httpMock.expectOne('/api/feature-model/guided-workflow');
        expect(request.request.method).toBe('GET');
        request.flush(workflow);
        expect(received).toEqual(workflow);
    });

    it('issues a GET request to /api/feature-model/profile-availability without a profile id', () => {
        service.loadWorkflowAvailability().subscribe();

        const request = httpMock.expectOne('/api/feature-model/profile-availability');
        expect(request.request.method).toBe('GET');
        expect(request.request.params.has('profileId')).toBe(false);
        request.flush({ activeProfile: undefined, availableProfiles: [], options: [], features: [] });
    });

    it('passes the profile id as a query parameter when requesting availability', () => {
        service.loadWorkflowAvailability('ai-enabled-profile').subscribe();

        const request = httpMock.expectOne((candidate) => candidate.url === '/api/feature-model/profile-availability');
        expect(request.request.params.get('profileId')).toBe('ai-enabled-profile');
        request.flush({ activeProfile: undefined, availableProfiles: [], options: [], features: [] });
    });

    it('issues a GET request to /api/feature-model/snapshots', () => {
        const snapshots: SnapshotSummary[] = [
            {
                snapshotId: 'develop-latest',
                modelId: 'artemis-feature-model',
                version: '1.0.0',
                status: 'development',
                sourceRepo: 'ls1intum/Artemis',
                sourceRef: 'develop',
                sourceCommit: 'abc123',
                extractorVersion: 'feature-model-extractor@0.2.0',
                active: true,
            },
        ];
        let received: SnapshotSummary[] | undefined;

        service.loadSnapshots().subscribe((value) => {
            received = value;
        });

        const request = httpMock.expectOne('/api/feature-model/snapshots');
        expect(request.request.method).toBe('GET');
        request.flush(snapshots);
        expect(received).toEqual(snapshots);
    });
});
