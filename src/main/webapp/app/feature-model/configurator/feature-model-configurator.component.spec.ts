import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { buildGuidedWorkflowFixture, buildMvpFeatureModelResponse } from '../core/feature-model.test-fixtures';
import { FeatureModelResponse, ValidationRequest, ValidationResult } from '../core/feature-model.types';
import { GuidedWorkflow } from '../core/guided-workflow.types';
import { FeatureModelConfiguratorComponent } from './feature-model-configurator.component';

const MODEL_URL = '/api/feature-model';
const GUIDED_WORKFLOW_URL = '/api/feature-model/guided-workflow';
const VALIDATE_URL = '/api/feature-model/validate';
const TUTORIAL_SEEN_KEY =
    'artemis.configurator.tutorial.seen:artemis-guided-configuration:0.1.0:artemis-functional-feature-tree:0.1.0';

function setup(): {
    fixture: ComponentFixture<FeatureModelConfiguratorComponent>;
    httpMock: HttpTestingController;
} {
    TestBed.configureTestingModule({
        imports: [FeatureModelConfiguratorComponent],
        providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const fixture = TestBed.createComponent(FeatureModelConfiguratorComponent);
    const httpMock = TestBed.inject(HttpTestingController);
    return { fixture, httpMock };
}

function rootEl(fixture: ComponentFixture<FeatureModelConfiguratorComponent>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
}

function validResult(selectedFeatureIds: readonly string[] = []): ValidationResult {
    return {
        valid: true,
        normalizedSelection: [...selectedFeatureIds],
        violations: [],
        warnings: [],
    };
}

function flushInitialLoads(
    fixture: ComponentFixture<FeatureModelConfiguratorComponent>,
    httpMock: HttpTestingController,
    response: FeatureModelResponse = buildMvpFeatureModelResponse(),
    workflow: GuidedWorkflow = buildGuidedWorkflowFixture(),
): ValidationRequest {
    fixture.detectChanges();

    const modelRequest = httpMock.expectOne(MODEL_URL);
    expect(modelRequest.request.method).toBe('GET');
    modelRequest.flush(response);

    const workflowRequest = httpMock.expectOne(GUIDED_WORKFLOW_URL);
    expect(workflowRequest.request.method).toBe('GET');
    workflowRequest.flush(workflow);

    fixture.detectChanges();
    return flushValidation(httpMock, validResult(response.defaultSelectedFeatureIds));
}

function flushValidation(httpMock: HttpTestingController, result: ValidationResult): ValidationRequest {
    const request = httpMock.expectOne(VALIDATE_URL);
    expect(request.request.method).toBe('POST');
    const body = request.request.body as ValidationRequest;
    request.flush({ ...result, normalizedSelection: result.normalizedSelection.length > 0 ? result.normalizedSelection : body.selectedFeatureIds });
    return body;
}

function clickByTestId(fixture: ComponentFixture<FeatureModelConfiguratorComponent>, testId: string): void {
    const element = rootEl(fixture).querySelector(`[data-testid="${testId}"]`) as HTMLElement;
    expect(element).not.toBeNull();
    element.click();
    fixture.detectChanges();
}

function markTutorialSeen(): void {
    window.localStorage.setItem(TUTORIAL_SEEN_KEY, 'true');
}

describe('FeatureModelConfiguratorComponent', () => {
    let fixture: ComponentFixture<FeatureModelConfiguratorComponent>;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        window.localStorage.clear();
        ({ fixture, httpMock } = setup());
    });

    afterEach(() => {
        httpMock.verify();
        window.localStorage.clear();
    });

    it('loads the feature model and guided workflow before rendering templates', () => {
        markTutorialSeen();
        fixture.detectChanges();
        expect(rootEl(fixture).querySelector('[data-testid="loading-state"]')?.textContent).toContain('Loading guided configurator');

        flushInitialLoads(fixture, httpMock);
        fixture.detectChanges();

        expect(rootEl(fixture).querySelector('h1')?.textContent).toBe('Artemis Configurator');
        expect(rootEl(fixture).textContent).toContain('Artemis Functional Feature Tree');
        expect(rootEl(fixture).querySelector('[data-testid="template-card-minimal-teaching-setup"]')).not.toBeNull();
        expect(rootEl(fixture).querySelector('[data-testid="advanced-tree-button"]')).not.toBeNull();
        expect(rootEl(fixture).querySelector('[data-testid="tutorial-help-button"]')).not.toBeNull();
        expect(rootEl(fixture).querySelector('[data-testid="advanced-explorer-link"]')).toBeNull();
    });

    it('renders an error panel when either guided load request fails', () => {
        markTutorialSeen();
        fixture.detectChanges();
        httpMock.expectOne(MODEL_URL).flush(buildMvpFeatureModelResponse());
        httpMock.expectOne(GUIDED_WORKFLOW_URL).flush('Boom', { status: 500, statusText: 'Server Error' });
        fixture.detectChanges();

        expect(rootEl(fixture).querySelector('[data-testid="error-state"]')).not.toBeNull();
    });

    it('preselects the expected features when a use-case template is chosen', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);

        clickByTestId(fixture, 'template-card-minimal-teaching-setup');
        const requestBody = flushValidation(httpMock, validResult());
        fixture.detectChanges();

        expect(requestBody.selectedFeatureIds).toEqual(['course-workflow', 'communication', 'exercise-common', 'programming', 'quiz']);
        expect(fixture.componentInstance.selectedFeatureIds().has('lecture')).toBe(false);
        expect(fixture.componentInstance.selectedFeatureIds().has('iris')).toBe(false);
        expect(rootEl(fixture).querySelector('[data-testid="selected-count"]')?.textContent?.trim()).toBe('5');
    });

    it('selects and deselects mapped features through decision options and validates each change', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        clickByTestId(fixture, 'template-card-minimal-teaching-setup');
        flushValidation(httpMock, validResult());
        clickByTestId(fixture, 'start-workflow');
        fixture.componentInstance.onJumpToStep(1);
        fixture.detectChanges();

        clickByTestId(fixture, 'option-card-enable-written-exercise-types');
        const selectedBody = flushValidation(httpMock, validResult());
        expect(selectedBody.selectedFeatureIds).toContain('text');
        expect(selectedBody.selectedFeatureIds).toContain('modeling');
        expect(selectedBody.selectedFeatureIds).toContain('file-upload');

        clickByTestId(fixture, 'option-card-enable-written-exercise-types');
        const deselectedBody = flushValidation(httpMock, validResult());
        expect(deselectedBody.selectedFeatureIds).not.toContain('text');
        expect(deselectedBody.selectedFeatureIds).not.toContain('modeling');
        expect(deselectedBody.selectedFeatureIds).not.toContain('file-upload');
    });

    it('shows consequence and artifact text for the active decision option', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        clickByTestId(fixture, 'start-workflow');
        fixture.componentInstance.onJumpToStep(2);
        fixture.detectChanges();

        clickByTestId(fixture, 'option-card-enable-iris');
        flushValidation(httpMock, validResult());
        fixture.detectChanges();

        const impact = rootEl(fixture).querySelector('[data-testid="impact-panel"]');
        expect(impact?.textContent).toContain('Requires Pyris service');
        expect(impact?.textContent).toContain('artemis.iris.enabled');
    });

    it('shows readable availability reasons for options that require profile capabilities', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        clickByTestId(fixture, 'start-workflow');
        fixture.componentInstance.onJumpToStep(2);
        fixture.detectChanges();

        const reason = rootEl(fixture).querySelector('[data-testid="option-unavailable-reason"]');
        expect(reason?.textContent).toContain('pyris-service');
        expect(reason?.textContent).toContain('pyris-secret');
    });

    it('renders validation feedback after selection changes', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        clickByTestId(fixture, 'start-workflow');
        fixture.componentInstance.onJumpToStep(1);
        fixture.detectChanges();

        clickByTestId(fixture, 'option-card-enable-programming-and-quiz');
        flushValidation(httpMock, {
            valid: false,
            normalizedSelection: [],
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
        });
        fixture.detectChanges();

        expect(rootEl(fixture).querySelector('[data-testid="validation-status-label"]')?.textContent).toBe('Configuration is invalid.');
        const violationsPanel = rootEl(fixture).querySelector('[data-testid="guided-violations-panel"]');
        expect(violationsPanel?.textContent).toContain('MANDATORY_FEATURE_MISSING');
        expect(violationsPanel?.textContent).toContain('Programming is mandatory under Exercise System.');
        expect(violationsPanel?.textContent).toContain('Enable Programming.');
    });

    it('summarizes selected features, warnings, validation, and artifact handoff on the review page', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        clickByTestId(fixture, 'template-card-ai-enabled-course');
        flushValidation(httpMock, validResult());
        clickByTestId(fixture, 'start-workflow');
        clickByTestId(fixture, 'next-step');
        clickByTestId(fixture, 'next-step');

        const review = rootEl(fixture).querySelector('[data-testid="review-screen"]');
        expect(review).not.toBeNull();
        expect(rootEl(fixture).querySelector('[data-testid="selected-features-summary"]')?.textContent).toContain('Iris');
        expect(rootEl(fixture).querySelector('[data-testid="warning-summary"]')?.textContent).toContain('AI options are only usable');
        expect(rootEl(fixture).querySelector('[data-testid="validation-summary"]')?.textContent).toContain('Configuration is valid.');
        expect(rootEl(fixture).querySelector('[data-testid="artifact-next-step"]')?.textContent).toContain('reserved for the next phase');
    });

    it('opens an in-configurator tree view that reflects and updates the current selection', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        fixture.detectChanges();

        clickByTestId(fixture, 'advanced-tree-button');
        expect(rootEl(fixture).querySelector('[data-testid="configurator-tree-screen"]')).not.toBeNull();
        expect(rootEl(fixture).querySelector('[data-testid="advanced-explorer-link"]')).toBeNull();

        clickByTestId(fixture, 'tree-expand-all');
        const lectureNode = rootEl(fixture).querySelector('.diagram-node[data-feature-id="lecture"]');
        expect(lectureNode?.classList.contains('diagram-node--configured-selected')).toBe(true);

        (lectureNode as HTMLElement).dispatchEvent(new MouseEvent('click', { bubbles: true }));
        fixture.detectChanges();
        const requestBody = flushValidation(httpMock, validResult());
        fixture.detectChanges();

        expect(requestBody.selectedFeatureIds).not.toContain('lecture');
        expect(fixture.componentInstance.selectedFeatureIds().has('lecture')).toBe(false);
    });

    it('opens the tutorial automatically on the first visit and persists skip state', () => {
        flushInitialLoads(fixture, httpMock);
        fixture.detectChanges();

        expect(rootEl(fixture).querySelector('[data-testid="tutorial-panel"]')).not.toBeNull();
        expect(rootEl(fixture).querySelector('[data-testid="tutorial-panel"]')?.textContent).toContain('Configurator Tutorial');
        expect(rootEl(fixture).querySelector('[data-testid="tutorial-panel"]')?.textContent).toContain('1 of 5');
        expect(rootEl(fixture).querySelector('.tutorial-panel__image')).toBeNull();
        expect(window.localStorage.getItem(TUTORIAL_SEEN_KEY)).toBeNull();

        clickByTestId(fixture, 'tutorial-skip');

        expect(rootEl(fixture).querySelector('[data-testid="tutorial-panel"]')).toBeNull();
        expect(window.localStorage.getItem(TUTORIAL_SEEN_KEY)).toBe('true');
    });

    it('does not auto-open the tutorial when the versioned seen key exists', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        fixture.detectChanges();

        expect(rootEl(fixture).querySelector('[data-testid="tutorial-panel"]')).toBeNull();
    });

    it('opens the tutorial from the guided help button and navigates steps', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        fixture.detectChanges();

        clickByTestId(fixture, 'tutorial-help-button');
        expect(rootEl(fixture).querySelector('[data-testid="tutorial-panel"]')?.textContent).toContain('Configurator Tutorial');
        expect(rootEl(fixture).querySelector('[data-testid="tutorial-back"]')).toBeNull();

        clickByTestId(fixture, 'tutorial-next');
        expect(rootEl(fixture).querySelector('[data-testid="tutorial-panel"]')?.textContent).toContain('Choose a template');
        const tutorialImage = rootEl(fixture).querySelector('.tutorial-panel__image') as HTMLImageElement;
        expect(tutorialImage).not.toBeNull();
        expect(tutorialImage.getAttribute('src')).toBe('content/img/tutorial/templates.png');
        expect(tutorialImage.getAttribute('src')).not.toContain('src/main/webapp');
        expect(tutorialImage.classList.contains('tutorial-panel__image--wide')).toBe(true);
        expect(rootEl(fixture).querySelector('[data-testid="tutorial-back"]')).not.toBeNull();

        clickByTestId(fixture, 'tutorial-next');
        expect(rootEl(fixture).querySelector('[data-testid="tutorial-panel"]')?.textContent).toContain('Decide on features');
        expect(rootEl(fixture).querySelector('[data-testid="tutorial-back"]')).not.toBeNull();

        clickByTestId(fixture, 'tutorial-back');
        expect(rootEl(fixture).querySelector('[data-testid="tutorial-panel"]')?.textContent).toContain('Choose a template');
    });

    it('finishes the tutorial and writes the versioned seen key', () => {
        flushInitialLoads(fixture, httpMock);
        fixture.detectChanges();

        clickByTestId(fixture, 'tutorial-next');
        clickByTestId(fixture, 'tutorial-next');
        clickByTestId(fixture, 'tutorial-next');
        clickByTestId(fixture, 'tutorial-next');
        expect(rootEl(fixture).querySelector('[data-testid="tutorial-panel"]')?.textContent).toContain('Use the live tree view');
        const treeImage = rootEl(fixture).querySelector('.tutorial-panel__image') as HTMLImageElement;
        expect(treeImage).not.toBeNull();
        expect(treeImage.getAttribute('src')).toBe('content/img/tutorial/tree.png');
        expect(treeImage.classList.contains('tutorial-panel__image--wide')).toBe(true);
        clickByTestId(fixture, 'tutorial-finish');

        expect(rootEl(fixture).querySelector('[data-testid="tutorial-panel"]')).toBeNull();
        expect(window.localStorage.getItem(TUTORIAL_SEEN_KEY)).toBe('true');
    });

    it('hides the guided tutorial help button in advanced tree mode', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        fixture.detectChanges();

        expect(rootEl(fixture).querySelector('[data-testid="tutorial-help-button"]')).not.toBeNull();
        clickByTestId(fixture, 'advanced-tree-button');

        expect(rootEl(fixture).querySelector('[data-testid="configurator-tree-screen"]')).not.toBeNull();
        expect(rootEl(fixture).querySelector('[data-testid="tutorial-help-button"]')).toBeNull();
    });
});
