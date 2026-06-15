import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { buildGuidedWorkflowFixture, buildMvpFeatureModelResponse, buildWorkflowAvailabilityFixture } from '../core/feature-model.test-fixtures';
import { WorkflowAvailability } from '../core/deployment-profile.types';
import { FeatureModelResponse, ValidationRequest, ValidationResult } from '../core/feature-model.types';
import { GuidedWorkflow } from '../core/guided-workflow.types';
import { FeatureModelConfiguratorComponent } from './feature-model-configurator.component';

const MODEL_URL = '/api/feature-model';
const GUIDED_WORKFLOW_URL = '/api/feature-model/guided-workflow';
const PROFILE_AVAILABILITY_URL = '/api/feature-model/profile-availability';
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
    availability: WorkflowAvailability = buildWorkflowAvailabilityFixture(),
): ValidationRequest {
    fixture.detectChanges();

    const modelRequest = httpMock.expectOne(MODEL_URL);
    expect(modelRequest.request.method).toBe('GET');
    modelRequest.flush(response);

    const workflowRequest = httpMock.expectOne(GUIDED_WORKFLOW_URL);
    expect(workflowRequest.request.method).toBe('GET');
    workflowRequest.flush(workflow);

    const availabilityRequest = httpMock.expectOne(PROFILE_AVAILABILITY_URL);
    expect(availabilityRequest.request.method).toBe('GET');
    availabilityRequest.flush(availability);

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

    it('renders an error panel when any initial load request fails', () => {
        markTutorialSeen();
        fixture.detectChanges();
        httpMock.expectOne(MODEL_URL).flush(buildMvpFeatureModelResponse());
        httpMock.expectOne(PROFILE_AVAILABILITY_URL).flush(buildWorkflowAvailabilityFixture());
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

    it('marks default-on guided options as selected for the custom configuration template', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        fixture.detectChanges();

        expect(rootEl(fixture).querySelector('[data-testid="selected-count"]')?.textContent?.trim()).toBe('13');

        clickByTestId(fixture, 'start-workflow');
        expect(rootEl(fixture).querySelector('[data-testid="option-card-enable-lecture-materials"]')?.classList).toContain(
            'option-card--selected',
        );

        fixture.componentInstance.onJumpToStep(1);
        fixture.detectChanges();

        expect(rootEl(fixture).querySelector('[data-testid="option-card-enable-programming-and-quiz"]')?.classList).toContain(
            'option-card--selected',
        );
        expect(rootEl(fixture).querySelector('[data-testid="option-card-enable-written-exercise-types"]')?.classList).toContain(
            'option-card--selected',
        );
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

    it('shows regular-user guidance for the active decision option', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        clickByTestId(fixture, 'start-workflow');
        fixture.componentInstance.onJumpToStep(2);
        fixture.detectChanges();

        clickByTestId(fixture, 'option-card-enable-iris');
        flushValidation(httpMock, validResult());
        fixture.detectChanges();

        const impact = rootEl(fixture).querySelector('[data-testid="impact-panel"]');
        expect(impact?.textContent).toContain('What this enables');
        expect(impact?.textContent).toContain('Students and instructors can receive AI tutoring support.');
        expect(impact?.textContent).toContain('Recommended when');
        expect(impact?.textContent).toContain('Things to know');
        expect(impact?.textContent).not.toContain('pyris-service');
        expect(impact?.textContent).not.toContain('artemis.iris.enabled');
    });

    it('does not render a deployment profile selector in the regular UI', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        fixture.detectChanges();

        expect(rootEl(fixture).querySelector('[data-testid="profile-select"]')).toBeNull();
        expect(rootEl(fixture).querySelector('[data-testid="profile-selector"]')).toBeNull();
    });

    it('keeps capability-dependent options selectable under the bundled deployment context', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        clickByTestId(fixture, 'start-workflow');
        fixture.componentInstance.onJumpToStep(2);
        fixture.detectChanges();

        const irisCard = rootEl(fixture).querySelector('[data-testid="option-card-enable-iris"]') as HTMLButtonElement;
        expect(irisCard.disabled).toBe(false);
        // A capability-dependent but available option carries a neutral "requires setup" note, not a blocking reason.
        expect(rootEl(fixture).querySelector('[data-testid="option-unavailable-reason"]')).toBeNull();

        clickByTestId(fixture, 'option-card-enable-iris');
        const body = flushValidation(httpMock, validResult());
        expect(body.selectedFeatureIds).toContain('iris');
        expect(fixture.componentInstance.selectedFeatureIds().has('iris')).toBe(true);
    });

    it('still gates options when a maintainer override restricts capabilities', () => {
        markTutorialSeen();
        // Simulate a local override profile that provides none of the AI/integration capabilities.
        flushInitialLoads(fixture, httpMock, buildMvpFeatureModelResponse(), buildGuidedWorkflowFixture(), buildWorkflowAvailabilityFixture({ providedCapabilities: [] }));
        clickByTestId(fixture, 'start-workflow');
        fixture.componentInstance.onJumpToStep(2);
        fixture.detectChanges();

        const irisCard = rootEl(fixture).querySelector('[data-testid="option-card-enable-iris"]') as HTMLButtonElement;
        expect(irisCard.disabled).toBe(true);
        expect(irisCard.classList).toContain('option-card--unavailable');

        const reason = rootEl(fixture).querySelector('[data-testid="option-unavailable-reason"]');
        expect(reason?.textContent).toContain('not available in the current deployment');
        expect(reason?.textContent).not.toContain('pyris-service');
        expect(reason?.textContent).not.toContain('pyris-secret');
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

    it('shows a neutral deployment context note and the features that need setup on the review page', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        clickByTestId(fixture, 'template-card-ai-enabled-course');
        flushValidation(httpMock, validResult());
        clickByTestId(fixture, 'start-workflow');
        clickByTestId(fixture, 'next-step');
        clickByTestId(fixture, 'next-step');

        const review = rootEl(fixture).querySelector('[data-testid="review-screen"]');
        expect(review).not.toBeNull();
        // The deployment context is informational, never presented as a user-selectable profile.
        const context = rootEl(fixture).querySelector('[data-testid="review-deployment-context"]');
        expect(context?.textContent).toContain('Deployment context loaded');
        expect(context?.textContent).not.toContain('Default Artemis Deployment Context');

        // Iris is selectable under the bundled context and listed as needing deployment setup.
        expect(rootEl(fixture).querySelector('[data-testid="selected-features-summary"]')?.textContent).toContain('Iris');
        const profileDependent = rootEl(fixture).querySelector('[data-testid="profile-dependent-features"]');
        expect(profileDependent?.textContent).toContain('Iris');
        expect(profileDependent?.textContent).toContain('need');

        expect(rootEl(fixture).querySelector('[data-testid="validation-summary"]')?.textContent).toContain('Configuration is valid.');
        expect(rootEl(fixture).querySelector('[data-testid="artifact-next-step"]')?.textContent).toContain('reserved for the next phase');
    });

    it('shows exact missing capability ids in the advanced tree debug view under a restricted override', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock, buildMvpFeatureModelResponse(), buildGuidedWorkflowFixture(), buildWorkflowAvailabilityFixture({ providedCapabilities: [] }));
        fixture.detectChanges();

        clickByTestId(fixture, 'advanced-tree-button');
        clickByTestId(fixture, 'tree-expand-all');
        const irisNode = rootEl(fixture).querySelector('.diagram-node[data-feature-id="iris"]');
        (irisNode as HTMLElement).dispatchEvent(new MouseEvent('click', { bubbles: true }));
        // Toggling the unavailable Iris node reconciles it back out and revalidates the unchanged selection.
        flushValidation(httpMock, validResult());
        fixture.detectChanges();

        const profileAvailability = rootEl(fixture).querySelector('[data-testid="tree-profile-availability"]');
        expect(profileAvailability?.textContent).toContain('Unavailable');
        const missing = rootEl(fixture).querySelector('[data-testid="tree-missing-capabilities"]');
        expect(missing?.textContent).toContain('pyris-service');
        expect(missing?.textContent).toContain('pyris-secret');
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
        const artifactImpact = rootEl(fixture).querySelector('[data-testid="tree-artifact-impact"]');
        expect(artifactImpact?.textContent).toContain('artemis.lecture.enabled');
        expect(rootEl(fixture).querySelector('[data-testid="tree-technical-impact"]')?.textContent).toContain('No additional deployment capability');
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
