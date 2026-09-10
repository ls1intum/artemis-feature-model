import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { buildGuidedWorkflowFixture, buildMvpFeatureModelResponse, buildWorkflowAvailabilityFixture } from '../core/feature-model.test-fixtures';
import { DeploymentPackagePublishTarget } from '../core/artifact-generation.types';
import { WorkflowAvailability } from '../core/deployment-profile.types';
import { Feature, FeatureModelResponse, FeatureTreeNode, ValidationRequest, ValidationResult } from '../core/feature-model.types';
import { GuidedWorkflow } from '../core/guided-workflow.types';
import { FeatureModelConfiguratorComponent } from './feature-model-configurator.component';

const MODEL_URL = '/api/feature-model';
const GUIDED_WORKFLOW_URL = '/api/feature-model/guided-workflow';
const PROFILE_AVAILABILITY_URL = '/api/feature-model/profile-availability';
const VALIDATE_URL = '/api/feature-model/validate';
const ARTIFACTS_DOWNLOAD_URL = '/api/feature-model/artifacts/download';
const DEPLOYMENT_PACKAGE_DOWNLOAD_URL = '/api/feature-model/deployment-package/download';
const DEPLOYMENT_PACKAGE_PUBLISH_URL = '/api/feature-model/deployment-package/publish';
const PUBLISH_TARGET_URL = '/api/feature-model/deployment-package/publish-target';
const TARGET_NAME_STORAGE_KEY = 'artemis.configurator.deployment.target-name';
const TUTORIAL_SEEN_KEY =
    'artemis.configurator.tutorial.seen:artemis-guided-configuration:0.1.0:artemis-functional-feature-tree:0.1.0';

const UNCONFIGURED_PUBLISH_TARGET: DeploymentPackagePublishTarget = {
    configured: false,
    repositoryUrl: null,
    branch: 'main',
    targetDirectoryRoot: 'deployments',
};

const CONFIGURED_PUBLISH_TARGET: DeploymentPackagePublishTarget = {
    configured: true,
    repositoryUrl: 'https://github.com/example/deployments.git',
    branch: 'deployment',
    targetDirectoryRoot: 'deployments',
};

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
    publishTarget: DeploymentPackagePublishTarget = UNCONFIGURED_PUBLISH_TARGET,
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

    const publishTargetRequest = httpMock.expectOne(PUBLISH_TARGET_URL);
    expect(publishTargetRequest.request.method).toBe('GET');
    publishTargetRequest.flush(publishTarget);

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

function typeTargetName(fixture: ComponentFixture<FeatureModelConfiguratorComponent>, targetName: string): void {
    const input = rootEl(fixture).querySelector('[data-testid="deployment-target-name-input"]') as HTMLInputElement;
    expect(input).not.toBeNull();
    input.value = targetName;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
}

function markTutorialSeen(): void {
    window.localStorage.setItem(TUTORIAL_SEEN_KEY, 'true');
}

function buildResponseWithApollonConstraint(): FeatureModelResponse {
    const response = buildMvpFeatureModelResponse();
    const theia = response.features.find((feature) => feature.id === 'theia');
    if (!theia) {
        throw new Error('Test fixture is missing theia.');
    }
    response.features = [
        ...response.features,
        {
            ...theia,
            id: 'apollon',
            name: 'Apollon',
            description: 'Optional UML diagram PDF export for modeling exercises.',
            defaultState: 'disabled',
            requiresCapabilities: ['apollon-conversion-service'],
        },
    ];
    response.constraints = [
        {
            id: 'apollon-requires-modeling',
            type: 'requires',
            source: 'apollon',
            target: 'modeling',
            expression: null,
            description: 'Apollon PDF conversion is only meaningful for modeling exercise UML models in the functional feature model.',
        },
    ];
    return response;
}

function buildWorkflowWithApollonOption(): GuidedWorkflow {
    const workflow = buildGuidedWorkflowFixture();
    const integrationStep = workflow.steps.find((step) => step.id === 'ai-and-integrations');
    const decision = integrationStep?.decisions[0];
    if (!decision) {
        throw new Error('Test fixture is missing the integration decision.');
    }
    decision.options.push({
        id: 'enable-apollon',
        label: 'Enable Apollon',
        description: 'Enable UML diagram PDF export through the Apollon conversion service.',
        selects: ['apollon'],
        deselects: [],
        requiresCapabilities: ['apollon-conversion-service'],
        artifactImpacts: ['Sets artemis.apollon.enabled = true in the generated external configuration overlay.'],
        enabledOutcome: ['Instructors and students can export UML diagrams from modeling exercises as PDF files.'],
        recommendedWhen: ['Your course uses modeling exercises and needs PDF exports of UML diagrams.'],
        thingsToKnow: ['Apollon depends on the Modeling feature being useful in the selected course setup.'],
        warnings: ['Only available when the active deployment profile provides the Apollon conversion service.'],
    });
    return workflow;
}

function buildTechnicalModelResponse(): FeatureModelResponse {
    const response = buildMvpFeatureModelResponse();
    const mysql = technicalFeature('mysql', 'MySQL', true);
    const postgresql = technicalFeature('postgresql', 'PostgreSQL', false);
    const integratedCodeLifecycle = technicalFeature('integrated-code-lifecycle', 'Integrated Code Lifecycle', true);
    const jenkins = technicalFeature('jenkins', 'Jenkins', false);
    const localvc = technicalFeature('localvc', 'Local Version Control', true);
    const database = technicalGroup('database', 'Database');
    const ciProvider = technicalGroup('ci-provider', 'CI Provider');

    const databaseTree = alternativeGroupTree(database, [mysql, postgresql], 6);
    const ciProviderTree = alternativeGroupTree(ciProvider, [integratedCodeLifecycle, jenkins], 7);
    const localvcTree: FeatureTreeNode = {
        feature: localvc,
        incomingRelation: { parentId: 'artemis', childId: 'localvc', relationType: 'mandatory', groupType: null, order: 8 },
        children: [],
    };

    response.features.push(database, mysql, postgresql, ciProvider, integratedCodeLifecycle, jenkins, localvc);
    response.tree.children.push(databaseTree, ciProviderTree, localvcTree);
    response.defaultSelectedFeatureIds.push('mysql', 'integrated-code-lifecycle', 'localvc');
    return response;
}

function buildTechnicalAvailability(): WorkflowAvailability {
    const availability = buildWorkflowAvailabilityFixture();
    for (const feature of ['mysql', 'postgresql', 'integrated-code-lifecycle', 'jenkins', 'localvc']) {
        availability.features.push({
            featureId: feature,
            featureName: feature,
            available: false,
            profileDependent: false,
            requiredCapabilities: [],
            missingCapabilities: [],
            teacherReason: `${feature} is not configurable in the current role.`,
        });
    }
    return availability;
}

function technicalFeature(id: string, name: string, enabledByDefault: boolean): Feature {
    return {
        id,
        name,
        kind: 'feature',
        selectable: true,
        description: null,
        defaultState: enabledByDefault ? 'enabled' : 'disabled',
        source: null,
        category: 'technical',
        visibleTo: ['maintainer'],
        configurableBy: ['maintainer'],
        requiresCapabilities: [],
        artifactMappings: [],
        extraction: null,
    };
}

function technicalGroup(id: string, name: string): Feature {
    return {
        ...technicalFeature(id, name, false),
        kind: 'group',
        selectable: false,
        defaultState: 'not_applicable',
        configurableBy: [],
    };
}

function alternativeGroupTree(group: Feature, alternatives: Feature[], order: number): FeatureTreeNode {
    return {
        feature: group,
        incomingRelation: { parentId: 'artemis', childId: group.id, relationType: 'group', groupType: 'alternative', order },
        children: alternatives.map((alternative, index) => ({
            feature: alternative,
            incomingRelation: {
                parentId: group.id,
                childId: alternative.id,
                relationType: 'optional',
                groupType: null,
                order: index + 1,
            },
            children: [],
        })),
    };
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
        // A failed publish-target probe only hides the publish action; it never blocks or unblocks the page.
        httpMock.expectOne(PUBLISH_TARGET_URL).flush('Boom', { status: 500, statusText: 'Server Error' });
        fixture.detectChanges();

        expect(rootEl(fixture).querySelector('[data-testid="error-state"]')).not.toBeNull();
    });

    it('preselects the expected features when a use-case template is chosen', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);

        clickByTestId(fixture, 'template-card-minimal-teaching-setup');
        const requestBody = flushValidation(httpMock, validResult());
        fixture.detectChanges();

        // Guided-owned features follow the template presets, while functional features no guided option governs
        // (tutorialgroup, exam, plagiarism, atlas) keep their current default-on state.
        expect(requestBody.selectedFeatureIds).toHaveLength(9);
        expect(requestBody.selectedFeatureIds).toEqual(
            expect.arrayContaining([
                'course-workflow',
                'communication',
                'exercise-common',
                'programming',
                'quiz',
                'tutorialgroup',
                'exam',
                'plagiarism',
                'atlas',
            ]),
        );
        expect(fixture.componentInstance.selectedFeatureIds().has('lecture')).toBe(false);
        expect(fixture.componentInstance.selectedFeatureIds().has('iris')).toBe(false);
        expect(rootEl(fixture).querySelector('[data-testid="selected-count"]')?.textContent?.trim()).toBe('9');
    });

    it('preserves tree-only functional selections across template switches', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);

        // Tree-only edits: athena on and exam off; neither feature is governed by a guided option.
        const editedSelection = new Set(fixture.componentInstance.selectedFeatureIds());
        editedSelection.add('athena');
        editedSelection.delete('exam');
        fixture.componentInstance.onReplaceSelection(editedSelection);
        flushValidation(httpMock, validResult());

        clickByTestId(fixture, 'template-card-ai-enabled-course');
        const requestBody = flushValidation(httpMock, validResult());
        fixture.detectChanges();

        expect(requestBody.selectedFeatureIds).toEqual(expect.arrayContaining(['athena', 'iris', 'atlas', 'text']));
        expect(requestBody.selectedFeatureIds).not.toContain('exam');
        // Guided-owned features still follow the template: modeling is governed and not preset by the template.
        expect(requestBody.selectedFeatureIds).not.toContain('modeling');
    });

    it('keeps tree-only selections when switching to the custom configuration template', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);

        const editedSelection = new Set(fixture.componentInstance.selectedFeatureIds());
        editedSelection.add('athena');
        editedSelection.delete('exam');
        fixture.componentInstance.onReplaceSelection(editedSelection);
        flushValidation(httpMock, validResult());

        clickByTestId(fixture, 'template-card-custom-configuration');
        const requestBody = flushValidation(httpMock, validResult());
        fixture.detectChanges();

        expect(requestBody.selectedFeatureIds).toContain('athena');
        expect(requestBody.selectedFeatureIds).not.toContain('exam');
        // Guided-owned features return to the model defaults for the default template.
        expect(requestBody.selectedFeatureIds).toEqual(expect.arrayContaining(['lecture', 'exercise-common', 'programming', 'quiz']));
    });

    it('preserves non-guided technical defaults when a template is chosen', () => {
        markTutorialSeen();
        const response = buildTechnicalModelResponse();
        flushInitialLoads(fixture, httpMock, response, buildGuidedWorkflowFixture(), buildTechnicalAvailability());

        clickByTestId(fixture, 'template-card-minimal-teaching-setup');
        const requestBody = flushValidation(httpMock, validResult());

        expect(requestBody.selectedFeatureIds).toEqual(expect.arrayContaining(['mysql', 'integrated-code-lifecycle', 'localvc']));
        expect(requestBody.selectedFeatureIds).not.toContain('postgresql');
        expect(requestBody.selectedFeatureIds).not.toContain('jenkins');
        expect(fixture.componentInstance.selectedFeatureIds().has('localvc')).toBe(true);
    });

    it('preserves role-restricted technical features during tree selection reconciliation', () => {
        markTutorialSeen();
        const response = buildTechnicalModelResponse();
        flushInitialLoads(fixture, httpMock, response, buildGuidedWorkflowFixture(), buildTechnicalAvailability());
        const editedSelection = new Set(fixture.componentInstance.selectedFeatureIds());
        editedSelection.delete('mysql');
        editedSelection.add('postgresql');

        fixture.componentInstance.onReplaceSelection(editedSelection);
        const requestBody = flushValidation(httpMock, validResult());

        expect(requestBody.selectedFeatureIds).toEqual(expect.arrayContaining(['postgresql', 'integrated-code-lifecycle', 'localvc']));
        expect(requestBody.selectedFeatureIds).not.toContain('mysql');
        expect(fixture.componentInstance.profileReconciliationNote()).toBeUndefined();
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

    it('offers every option of a step regardless of the template, leaving requires gaps to validation', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock, buildResponseWithApollonConstraint(), buildWorkflowWithApollonOption());
        clickByTestId(fixture, 'template-card-minimal-teaching-setup');
        flushValidation(httpMock, validResult());
        clickByTestId(fixture, 'start-workflow');

        const stepIndex = (id: string) => fixture.componentInstance.decisionSteps().findIndex((step) => step.id === id);
        fixture.componentInstance.onJumpToStep(stepIndex('ai-and-integrations'));
        fixture.detectChanges();

        // Modeling is not part of this template, but Apollon is still offered rather than silently hidden.
        expect(fixture.componentInstance.selectedFeatureIds().has('modeling')).toBe(false);
        expect(rootEl(fixture).querySelector('[data-testid="option-card-enable-apollon"]')).not.toBeNull();

        clickByTestId(fixture, 'option-card-enable-apollon');
        const apollonBody = flushValidation(httpMock, validResult());
        expect(apollonBody.selectedFeatureIds).toContain('apollon');
        expect(apollonBody.selectedFeatureIds).not.toContain('modeling');

        // Deselecting Modeling no longer drops Apollon behind the user's back; validation reports the gap.
        fixture.componentInstance.onJumpToStep(stepIndex('exercise-types'));
        fixture.detectChanges();
        clickByTestId(fixture, 'option-card-enable-written-exercise-types');
        flushValidation(httpMock, validResult());
        clickByTestId(fixture, 'option-card-enable-written-exercise-types');
        const deselectedBody = flushValidation(httpMock, validResult());

        expect(deselectedBody.selectedFeatureIds).not.toContain('modeling');
        expect(deselectedBody.selectedFeatureIds).toContain('apollon');
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
        // The artifacts-only export section is temporarily hidden; the deployment package picker is the export path.
        expect(rootEl(fixture).querySelector('[data-testid="artifact-generation"]')).toBeNull();
        expect(rootEl(fixture).querySelector('[data-testid="generate-artifacts-button"]')).toBeNull();
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

    it('keeps the artifact ZIP download logic working while its review-page section is hidden', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        fixture.componentInstance.onOpenReview();
        fixture.detectChanges();

        // The artifacts-only section is temporarily hidden from the review page; the download logic is retained.
        expect(rootEl(fixture).querySelector('[data-testid="artifact-generation"]')).toBeNull();

        fixture.componentInstance.onGenerateArtifacts();
        const downloadRequest = httpMock.expectOne(ARTIFACTS_DOWNLOAD_URL);
        expect(downloadRequest.request.method).toBe('POST');
        expect(downloadRequest.request.responseType).toBe('blob');
        expect((downloadRequest.request.body as { selectedFeatureIds: string[] }).selectedFeatureIds).toContain('programming');
        downloadRequest.flush(new Blob(['zip-bytes']));
        fixture.detectChanges();

        // No preview UI is rendered anymore.
        expect(rootEl(fixture).querySelector('[data-testid="artifact-result"]')).toBeNull();
        expect(rootEl(fixture).querySelector('[data-testid="download-artifacts-button"]')).toBeNull();
    });

    it('renders the Docker runtime package section without local wording and downloads it as a blob', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        fixture.componentInstance.onOpenReview();
        fixture.detectChanges();

        const packageSection = rootEl(fixture).querySelector('[data-testid="deployment-package"]');
        expect(packageSection).not.toBeNull();
        expect(packageSection?.textContent).toContain('Docker runtime');
        expect(packageSection?.textContent?.toLowerCase()).not.toContain('local');
        const button = rootEl(fixture).querySelector('[data-testid="download-deployment-package-button"]') as HTMLButtonElement;
        expect(button).not.toBeNull();
        expect(button.disabled).toBe(false);

        button.click();
        fixture.detectChanges();

        const downloadRequest = httpMock.expectOne(DEPLOYMENT_PACKAGE_DOWNLOAD_URL);
        expect(downloadRequest.request.method).toBe('POST');
        expect(downloadRequest.request.responseType).toBe('blob');
        expect((downloadRequest.request.body as { selectedFeatureIds: string[] }).selectedFeatureIds).toContain('programming');
        downloadRequest.flush(new Blob(['zip-bytes']));
    });

    it('blocks the export and says so while a revalidation is still in flight', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        fixture.componentInstance.onOpenReview();
        fixture.detectChanges();

        const downloadButton = () => rootEl(fixture).querySelector('[data-testid="download-deployment-package-button"]') as HTMLButtonElement;
        expect(downloadButton().disabled).toBe(false);

        // A tree edit starts a new validation; until it answers, the previous verdict must not gate the export.
        const edited = new Set(fixture.componentInstance.selectedFeatureIds());
        edited.add('athena');
        fixture.componentInstance.onReplaceSelection(edited);
        fixture.detectChanges();

        expect(rootEl(fixture).querySelector('[data-testid="validation-summary"]')?.textContent).toContain('Rechecking');
        expect(downloadButton().disabled).toBe(true);

        flushValidation(httpMock, validResult());
        fixture.detectChanges();

        expect(rootEl(fixture).querySelector('[data-testid="validation-summary"]')?.textContent).toContain('Configuration is valid.');
        expect(downloadButton().disabled).toBe(false);
    });

    it('preselects the local Docker target and omits the deployment mode from the default download request', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        fixture.componentInstance.onOpenReview();
        fixture.detectChanges();

        const defaultRadio = rootEl(fixture).querySelector('[data-testid="deployment-mode-local-docker"]') as HTMLInputElement;
        expect(defaultRadio).not.toBeNull();
        expect(defaultRadio.checked).toBe(true);

        clickByTestId(fixture, 'download-deployment-package-button');
        const downloadRequest = httpMock.expectOne(DEPLOYMENT_PACKAGE_DOWNLOAD_URL);
        // The default target preserves the pre-mode-axis request shape: no deploymentMode field at all.
        expect((downloadRequest.request.body as { deploymentMode?: string }).deploymentMode).toBeUndefined();
        downloadRequest.flush(new Blob(['zip-bytes']));
    });

    it('switches to the dev-ide target and sends the deployment mode with the download request', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        fixture.componentInstance.onOpenReview();
        fixture.detectChanges();

        clickByTestId(fixture, 'deployment-mode-dev-ide');

        expect(rootEl(fixture).querySelector('[data-testid="deployment-package-note"]')?.textContent).toContain('IntelliJ');
        const button = rootEl(fixture).querySelector('[data-testid="download-deployment-package-button"]') as HTMLButtonElement;
        expect(button.textContent).toContain('IDE setup package');

        clickByTestId(fixture, 'download-deployment-package-button');
        const downloadRequest = httpMock.expectOne(DEPLOYMENT_PACKAGE_DOWNLOAD_URL);
        expect((downloadRequest.request.body as { deploymentMode?: string }).deploymentMode).toBe('dev-ide');
        expect((downloadRequest.request.body as { selectedFeatureIds: string[] }).selectedFeatureIds).toContain('programming');
        downloadRequest.flush(new Blob(['zip-bytes']));
    });

    it('switches to the remote-ansible target and sends only the deployment mode with the download request', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        fixture.componentInstance.onOpenReview();
        fixture.detectChanges();

        clickByTestId(fixture, 'deployment-mode-remote-ansible');

        expect(rootEl(fixture).querySelector('[data-testid="deployment-package-note"]')?.textContent).toContain('admin-consumable');
        const button = rootEl(fixture).querySelector('[data-testid="download-deployment-package-button"]') as HTMLButtonElement;
        expect(button.textContent).toContain('Ansible deployment package');

        clickByTestId(fixture, 'download-deployment-package-button');
        const downloadRequest = httpMock.expectOne(DEPLOYMENT_PACKAGE_DOWNLOAD_URL);
        const requestBody = downloadRequest.request.body as { deploymentMode?: string; remoteEnvironment?: unknown; selectedFeatureIds: string[] };
        expect(requestBody.deploymentMode).toBe('remote-ansible');
        // Without a typed target name the request omits the target identity; the package uses the default group.
        expect(requestBody.remoteEnvironment).toBeUndefined();
        expect(requestBody.selectedFeatureIds).toContain('programming');
        downloadRequest.flush(new Blob(['zip-bytes']));
    });

    it('persists the remote target name and includes it in the download request', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        fixture.componentInstance.onOpenReview();
        fixture.detectChanges();

        clickByTestId(fixture, 'deployment-mode-remote-ansible');
        typeTargetName(fixture, 'artemis-remote');

        expect(window.localStorage.getItem(TARGET_NAME_STORAGE_KEY)).toBe('artemis-remote');
        clickByTestId(fixture, 'download-deployment-package-button');
        const downloadRequest = httpMock.expectOne(DEPLOYMENT_PACKAGE_DOWNLOAD_URL);
        expect((downloadRequest.request.body as { remoteEnvironment?: { targetName: string } }).remoteEnvironment).toEqual({ targetName: 'artemis-remote' });
        downloadRequest.flush(new Blob(['zip-bytes']));
    });

    it('restores the persisted remote target name into the field', () => {
        markTutorialSeen();
        window.localStorage.setItem(TARGET_NAME_STORAGE_KEY, 'artemis-remote');
        flushInitialLoads(fixture, httpMock, buildMvpFeatureModelResponse(), buildGuidedWorkflowFixture(), buildWorkflowAvailabilityFixture(),
            CONFIGURED_PUBLISH_TARGET);
        fixture.componentInstance.onOpenReview();
        fixture.detectChanges();

        clickByTestId(fixture, 'deployment-mode-remote-ansible');

        const input = rootEl(fixture).querySelector('[data-testid="deployment-target-name-input"]') as HTMLInputElement;
        expect(input.value).toBe('artemis-remote');
        expect(rootEl(fixture).querySelector('[data-testid="publish-deployment-package-button"]')).not.toBeNull();
    });

    it('shows the publish action only for the remote target with a configured repository and a target name', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock, buildMvpFeatureModelResponse(), buildGuidedWorkflowFixture(), buildWorkflowAvailabilityFixture(),
            CONFIGURED_PUBLISH_TARGET);
        fixture.componentInstance.onOpenReview();
        fixture.detectChanges();

        // The default local Docker target has no target-name field and no publish action.
        expect(rootEl(fixture).querySelector('[data-testid="deployment-target-name-input"]')).toBeNull();
        expect(rootEl(fixture).querySelector('[data-testid="publish-deployment-package-button"]')).toBeNull();

        clickByTestId(fixture, 'deployment-mode-remote-ansible');
        expect(rootEl(fixture).querySelector('[data-testid="deployment-target-name-input"]')).not.toBeNull();
        expect(rootEl(fixture).querySelector('[data-testid="publish-deployment-package-button"]')).toBeNull();

        typeTargetName(fixture, 'Artemis Remote');
        const publishButton = rootEl(fixture).querySelector('[data-testid="publish-deployment-package-button"]');
        expect(publishButton).not.toBeNull();
        expect(rootEl(fixture).querySelector('[data-testid="publish-destination"]')?.textContent)
            .toContain('https://github.com/example/deployments.git@deployment/deployments/artemisremote');
    });

    it('hides the publish action on an unconfigured instance even with a target name', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        fixture.componentInstance.onOpenReview();
        fixture.detectChanges();

        clickByTestId(fixture, 'deployment-mode-remote-ansible');
        typeTargetName(fixture, 'artemis-remote');

        expect(rootEl(fixture).querySelector('[data-testid="publish-deployment-package-button"]')).toBeNull();
    });

    it('publishes and downloads with the same request body and renders both results', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock, buildMvpFeatureModelResponse(), buildGuidedWorkflowFixture(), buildWorkflowAvailabilityFixture(),
            CONFIGURED_PUBLISH_TARGET);
        fixture.componentInstance.onOpenReview();
        fixture.detectChanges();

        clickByTestId(fixture, 'deployment-mode-remote-ansible');
        typeTargetName(fixture, 'artemis-remote');
        clickByTestId(fixture, 'publish-deployment-package-button');

        const publishRequest = httpMock.expectOne(DEPLOYMENT_PACKAGE_PUBLISH_URL);
        expect(publishRequest.request.method).toBe('POST');
        const publishBody = publishRequest.request.body as { deploymentMode?: string; remoteEnvironment?: { targetName: string } };
        expect(publishBody.deploymentMode).toBe('remote-ansible');
        expect(publishBody.remoteEnvironment).toEqual({ targetName: 'artemis-remote' });
        publishRequest.flush({
            repositoryUrl: 'https://github.com/example/deployments.git',
            branch: 'deployment',
            targetDirectory: 'deployments/artemis-remote/package',
            commitSha: 'abcdef1234567890',
            commitUrl: 'https://github.com/example/deployments/commit/abcdef1234567890',
            upToDate: false,
        });
        fixture.detectChanges();

        const downloadRequest = httpMock.expectOne(DEPLOYMENT_PACKAGE_DOWNLOAD_URL);
        expect(downloadRequest.request.body).toEqual(publishRequest.request.body);
        downloadRequest.flush(new Blob(['zip-bytes']));
        fixture.detectChanges();

        const result = rootEl(fixture).querySelector('[data-testid="publish-result"]');
        expect(result?.textContent).toContain('Published as commit');
        expect(result?.querySelector('a')?.getAttribute('href')).toBe('https://github.com/example/deployments/commit/abcdef1234567890');
        expect(result?.textContent).toContain('abcdef1');
    });

    it('renders the up-to-date notice for an unchanged republish', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock, buildMvpFeatureModelResponse(), buildGuidedWorkflowFixture(), buildWorkflowAvailabilityFixture(),
            CONFIGURED_PUBLISH_TARGET);
        fixture.componentInstance.onOpenReview();
        fixture.detectChanges();

        clickByTestId(fixture, 'deployment-mode-remote-ansible');
        typeTargetName(fixture, 'artemis-remote');
        clickByTestId(fixture, 'publish-deployment-package-button');

        httpMock.expectOne(DEPLOYMENT_PACKAGE_PUBLISH_URL).flush({
            repositoryUrl: 'https://github.com/example/deployments.git',
            branch: 'deployment',
            targetDirectory: 'deployments/artemis-remote/package',
            commitSha: 'abcdef1234567890',
            commitUrl: 'https://github.com/example/deployments/commit/abcdef1234567890',
            upToDate: true,
        });
        httpMock.expectOne(DEPLOYMENT_PACKAGE_DOWNLOAD_URL).flush(new Blob(['zip-bytes']));
        fixture.detectChanges();

        expect(rootEl(fixture).querySelector('[data-testid="publish-result"]')?.textContent).toContain('already up to date');
    });

    it('still downloads after a publish failure and surfaces the publish error separately', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock, buildMvpFeatureModelResponse(), buildGuidedWorkflowFixture(), buildWorkflowAvailabilityFixture(),
            CONFIGURED_PUBLISH_TARGET);
        fixture.componentInstance.onOpenReview();
        fixture.detectChanges();

        clickByTestId(fixture, 'deployment-mode-remote-ansible');
        typeTargetName(fixture, 'artemis-remote');
        clickByTestId(fixture, 'publish-deployment-package-button');

        const serverMessage = 'The deployment repository rejected the publish: the remote refused the ref.';
        httpMock.expectOne(DEPLOYMENT_PACKAGE_PUBLISH_URL).flush({ code: 'PUBLISH_REJECTED', message: serverMessage },
            { status: 409, statusText: 'Conflict' });
        fixture.detectChanges();

        const downloadRequest = httpMock.expectOne(DEPLOYMENT_PACKAGE_DOWNLOAD_URL);
        downloadRequest.flush(new Blob(['zip-bytes']));
        fixture.detectChanges();

        expect(rootEl(fixture).querySelector('[data-testid="publish-error"]')?.textContent).toContain(serverMessage);
        expect(rootEl(fixture).querySelector('[data-testid="publish-result"]')).toBeNull();
    });

    it('disables the runtime package download while the selection is invalid', () => {
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
        fixture.componentInstance.onOpenReview();
        fixture.detectChanges();

        const button = rootEl(fixture).querySelector('[data-testid="download-deployment-package-button"]') as HTMLButtonElement;
        expect(button.disabled).toBe(true);
        expect(rootEl(fixture).querySelector('[data-testid="deployment-package-invalid-note"]')).not.toBeNull();
    });

    it('renders a runtime package download error', () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        fixture.componentInstance.onOpenReview();
        fixture.detectChanges();

        clickByTestId(fixture, 'download-deployment-package-button');
        httpMock.expectOne(DEPLOYMENT_PACKAGE_DOWNLOAD_URL).error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });
        fixture.detectChanges();

        expect(rootEl(fixture).querySelector('[data-testid="deployment-package-error"]')).not.toBeNull();
    });

    it('surfaces the server explanation from a blob error body of a deployment package download', async () => {
        markTutorialSeen();
        flushInitialLoads(fixture, httpMock);
        fixture.componentInstance.onOpenReview();
        fixture.detectChanges();

        clickByTestId(fixture, 'deployment-mode-remote-ansible');
        clickByTestId(fixture, 'download-deployment-package-button');
        const serverMessage = "Cannot generate a remote-ansible package: feature 'exam' is not expressible with the pinned collection.";
        const errorBody = new Blob([JSON.stringify({ code: 'ARTIFACT_GENERATION_REMOTE_ANSIBLE_UNSUPPORTED_FEATURE', message: serverMessage })], {
            type: 'application/json',
        });
        httpMock.expectOne(DEPLOYMENT_PACKAGE_DOWNLOAD_URL).flush(errorBody, { status: 400, statusText: 'Bad Request' });
        // Reading the blob body resolves asynchronously; yield to the event loop before checking the rendered message.
        await new Promise((resolve) => setTimeout(resolve, 0));
        fixture.detectChanges();

        expect(rootEl(fixture).querySelector('[data-testid="deployment-package-error"]')?.textContent).toContain(serverMessage);
    });

    it('still blocks artifact generation for an invalid selection while the section is hidden', () => {
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
        fixture.componentInstance.onOpenReview();
        fixture.detectChanges();

        fixture.componentInstance.onGenerateArtifacts();
        httpMock.expectNone(ARTIFACTS_DOWNLOAD_URL);
    });
});
