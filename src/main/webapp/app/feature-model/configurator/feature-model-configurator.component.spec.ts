import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { buildMvpFeatureModelResponse } from '../core/feature-model.test-fixtures';
import { FeatureModelResponse, ValidationRequest, ValidationResult } from '../core/feature-model.types';
import { FeatureModelConfiguratorComponent } from './feature-model-configurator.component';

const MODEL_URL = '/api/feature-model';
const VALIDATE_URL = '/api/feature-model/validate';

function setup(): {
    fixture: ComponentFixture<FeatureModelConfiguratorComponent>;
    httpMock: HttpTestingController;
} {
    TestBed.configureTestingModule({
        imports: [FeatureModelConfiguratorComponent],
        providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(FeatureModelConfiguratorComponent);
    const httpMock = TestBed.inject(HttpTestingController);
    return { fixture, httpMock };
}

function rootEl(fixture: ComponentFixture<FeatureModelConfiguratorComponent>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
}

function flushModel(httpMock: HttpTestingController, response: FeatureModelResponse): void {
    const request = httpMock.expectOne(MODEL_URL);
    expect(request.request.method).toBe('GET');
    request.flush(response);
}

function flushValidation(httpMock: HttpTestingController, result: ValidationResult): ValidationRequest {
    const request = httpMock.expectOne(VALIDATE_URL);
    expect(request.request.method).toBe('POST');
    const body = request.request.body as ValidationRequest;
    request.flush(result);
    return body;
}

function validResultFor(response: FeatureModelResponse): ValidationResult {
    return {
        valid: true,
        normalizedSelection: [...response.defaultSelectedFeatureIds],
        violations: [],
        warnings: [],
    };
}

function loadModelAndValidate(
    fixture: ComponentFixture<FeatureModelConfiguratorComponent>,
    httpMock: HttpTestingController,
    response: FeatureModelResponse = buildMvpFeatureModelResponse(),
): FeatureModelResponse {
    fixture.detectChanges();
    flushModel(httpMock, response);
    fixture.detectChanges();
    flushValidation(httpMock, validResultFor(response));
    fixture.detectChanges();
    return response;
}

describe('FeatureModelConfiguratorComponent', () => {
    let fixture: ComponentFixture<FeatureModelConfiguratorComponent>;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        ({ fixture, httpMock } = setup());
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('initial load', () => {
        it('renders the loading state before the model resolves', () => {
            fixture.detectChanges();
            const loading = rootEl(fixture).querySelector('[data-testid="loading-state"]');
            expect(loading).not.toBeNull();
            expect(loading?.textContent).toContain('Loading feature model');
            // Drain pending requests so afterEach verify passes.
            const response = buildMvpFeatureModelResponse();
            flushModel(httpMock, response);
            fixture.detectChanges();
            flushValidation(httpMock, validResultFor(response));
        });

        it('renders the model name and stats after the response loads', () => {
            loadModelAndValidate(fixture, httpMock);

            const heading = rootEl(fixture).querySelector('h1');
            expect(heading?.textContent).toBe('Artemis Functional Feature Tree');
            const selectedCount = rootEl(fixture).querySelector('[data-testid="selected-count"]');
            expect(selectedCount?.textContent?.trim()).toBe('13');
        });

        it('renders the tree diagram (and only the diagram) after the response loads', () => {
            loadModelAndValidate(fixture, httpMock);

            const diagram = rootEl(fixture).querySelector('fm-feature-model-diagram');
            expect(diagram).not.toBeNull();
            expect(rootEl(fixture).querySelector('fm-feature-model-tree-node')).toBeNull();
            expect(rootEl(fixture).querySelector('[data-testid="view-list"]')).toBeNull();
            expect(rootEl(fixture).querySelector('[data-testid="view-diagram"]')).toBeNull();
        });

        it('initializes selectedFeatureIds from defaultSelectedFeatureIds', () => {
            const response = loadModelAndValidate(fixture, httpMock);

            const selected = fixture.componentInstance.selectedFeatureIds();
            expect(selected.size).toBe(response.defaultSelectedFeatureIds.length);
            for (const id of response.defaultSelectedFeatureIds) {
                expect(selected.has(id)).toBe(true);
            }
        });

        it('focuses the root feature on load', () => {
            loadModelAndValidate(fixture, httpMock);

            expect(fixture.componentInstance.selectedFeatureId()).toBe('artemis');
            const detailsName = rootEl(fixture).querySelector('[data-testid="details-name"]');
            expect(detailsName?.textContent).toBe('Artemis');
        });

        it('renders the error panel when the model service fails', () => {
            fixture.detectChanges();
            const request = httpMock.expectOne(MODEL_URL);
            request.flush('Boom', { status: 500, statusText: 'Server Error' });
            fixture.detectChanges();

            const error = rootEl(fixture).querySelector('[data-testid="error-state"]');
            expect(error).not.toBeNull();
            expect(rootEl(fixture).querySelector('[data-testid="loading-state"]')).toBeNull();
        });
    });

    describe('toggle and reset', () => {
        it('removes a selectable module id when its toggle is invoked', () => {
            loadModelAndValidate(fixture, httpMock);

            expect(fixture.componentInstance.selectedFeatureIds().has('lecture')).toBe(true);
            fixture.componentInstance.onToggleSelection('lecture');
            flushValidation(httpMock, { valid: true, normalizedSelection: [], violations: [], warnings: [] });
            expect(fixture.componentInstance.selectedFeatureIds().has('lecture')).toBe(false);
        });

        it('adds a non-default selectable id when toggled on', () => {
            loadModelAndValidate(fixture, httpMock);

            expect(fixture.componentInstance.selectedFeatureIds().has('iris')).toBe(false);
            fixture.componentInstance.onToggleSelection('iris');
            flushValidation(httpMock, { valid: true, normalizedSelection: [], violations: [], warnings: [] });
            expect(fixture.componentInstance.selectedFeatureIds().has('iris')).toBe(true);
        });

        it('allows toggling mandatory modules off so invalid states can be demonstrated', () => {
            loadModelAndValidate(fixture, httpMock);

            expect(fixture.componentInstance.selectedFeatureIds().has('programming')).toBe(true);
            fixture.componentInstance.onToggleSelection('programming');
            flushValidation(httpMock, { valid: false, normalizedSelection: [], violations: [], warnings: [] });
            expect(fixture.componentInstance.selectedFeatureIds().has('programming')).toBe(false);
        });

        it('ignores toggle requests for structural root and group ids and does not call validation', () => {
            loadModelAndValidate(fixture, httpMock);

            const before = fixture.componentInstance.selectedFeatureIds();
            fixture.componentInstance.onToggleSelection('artemis');
            fixture.componentInstance.onToggleSelection('teaching-and-content');
            const after = fixture.componentInstance.selectedFeatureIds();
            expect(after.size).toBe(before.size);
            expect(after.has('artemis')).toBe(false);
            expect(after.has('teaching-and-content')).toBe(false);
            httpMock.expectNone(VALIDATE_URL);
        });

        it('restores the default selection when reset is clicked', () => {
            const response = loadModelAndValidate(fixture, httpMock);

            fixture.componentInstance.onToggleSelection('programming');
            flushValidation(httpMock, { valid: false, normalizedSelection: [], violations: [], warnings: [] });
            fixture.componentInstance.onToggleSelection('iris');
            flushValidation(httpMock, { valid: false, normalizedSelection: [], violations: [], warnings: [] });
            fixture.detectChanges();
            expect(fixture.componentInstance.changedFromDefault()).toBe(true);

            const resetButton = rootEl(fixture).querySelector('[data-testid="reset-defaults"]') as HTMLButtonElement;
            resetButton.click();
            flushValidation(httpMock, validResultFor(response));
            fixture.detectChanges();

            const defaults = fixture.componentInstance.defaultSelectedFeatureIds();
            const current = fixture.componentInstance.selectedFeatureIds();
            expect(current.size).toBe(defaults.size);
            for (const id of defaults) {
                expect(current.has(id)).toBe(true);
            }
            expect(fixture.componentInstance.changedFromDefault()).toBe(false);
        });

        it('disables reset when the selection equals the defaults', () => {
            loadModelAndValidate(fixture, httpMock);

            const resetButton = rootEl(fixture).querySelector('[data-testid="reset-defaults"]') as HTMLButtonElement;
            expect(resetButton.disabled).toBe(true);
        });

        it('renders an enable toggle for selectable features and updates the selected set when clicked', () => {
            loadModelAndValidate(fixture, httpMock);

            fixture.componentInstance.onSelectFeature('lecture');
            fixture.detectChanges();

            const toggle = rootEl(fixture).querySelector('[data-testid="details-toggle"]') as HTMLInputElement;
            expect(toggle).not.toBeNull();
            expect(toggle.checked).toBe(true);

            toggle.dispatchEvent(new Event('change'));
            flushValidation(httpMock, { valid: true, normalizedSelection: [], violations: [], warnings: [] });
            fixture.detectChanges();

            expect(fixture.componentInstance.selectedFeatureIds().has('lecture')).toBe(false);
            const refreshed = rootEl(fixture).querySelector('[data-testid="details-toggle"]') as HTMLInputElement;
            expect(refreshed.checked).toBe(false);
        });

        it('shows a structural message instead of a toggle for the root feature', () => {
            loadModelAndValidate(fixture, httpMock);

            expect(fixture.componentInstance.selectedFeatureId()).toBe('artemis');
            expect(rootEl(fixture).querySelector('[data-testid="details-toggle"]')).toBeNull();
            expect(rootEl(fixture).querySelector('[data-testid="structural-message"]')).not.toBeNull();
        });
    });

    describe('validation', () => {
        it('calls validation immediately after the defaults load with the default selection', () => {
            fixture.detectChanges();
            const response = buildMvpFeatureModelResponse();
            flushModel(httpMock, response);
            fixture.detectChanges();

            const body = flushValidation(httpMock, validResultFor(response));
            expect(body.selectedFeatureIds.length).toBe(response.defaultSelectedFeatureIds.length);
            for (const id of response.defaultSelectedFeatureIds) {
                expect(body.selectedFeatureIds).toContain(id);
            }
            fixture.detectChanges();
            expect(fixture.componentInstance.isValid()).toBe(true);
        });

        it('calls validation again after a toggle, with the updated selection', () => {
            loadModelAndValidate(fixture, httpMock);

            fixture.componentInstance.onToggleSelection('programming');
            const body = flushValidation(httpMock, {
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
            expect(body.selectedFeatureIds).not.toContain('programming');
            fixture.detectChanges();
            expect(fixture.componentInstance.isValid()).toBe(false);
        });

        it('renders a valid status panel for the default selection', () => {
            loadModelAndValidate(fixture, httpMock);

            const status = rootEl(fixture).querySelector('[data-testid="validation-status"]');
            expect(status).not.toBeNull();
            expect(status?.classList.contains('alert-success')).toBe(true);
            const label = rootEl(fixture).querySelector('[data-testid="validation-status-label"]');
            expect(label?.textContent).toBe('Configuration is valid.');
        });

        it('renders an invalid status panel when the result is invalid', () => {
            loadModelAndValidate(fixture, httpMock);

            fixture.componentInstance.onToggleSelection('programming');
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

            const status = rootEl(fixture).querySelector('[data-testid="validation-status"]');
            expect(status?.classList.contains('alert-danger')).toBe(true);
            const label = rootEl(fixture).querySelector('[data-testid="validation-status-label"]');
            expect(label?.textContent).toBe('Configuration is invalid.');
        });

        it('renders a validation error panel when the validation service fails', () => {
            fixture.detectChanges();
            const response = buildMvpFeatureModelResponse();
            flushModel(httpMock, response);
            fixture.detectChanges();

            const request = httpMock.expectOne(VALIDATE_URL);
            request.flush('Boom', { status: 500, statusText: 'Server Error' });
            fixture.detectChanges();

            const errorPanel = rootEl(fixture).querySelector('[data-testid="validation-error"]');
            expect(errorPanel).not.toBeNull();
        });

        it('shows a validation-loading indicator before the first result lands', () => {
            fixture.detectChanges();
            const response = buildMvpFeatureModelResponse();
            flushModel(httpMock, response);
            fixture.detectChanges();

            expect(rootEl(fixture).querySelector('[data-testid="validation-loading"]')).not.toBeNull();
            flushValidation(httpMock, validResultFor(response));
            fixture.detectChanges();
            expect(rootEl(fixture).querySelector('[data-testid="validation-loading"]')).toBeNull();
        });
    });

    describe('violations and warnings panel', () => {
        function disableProgrammingAndFlushViolation(): void {
            loadModelAndValidate(fixture, httpMock);
            fixture.componentInstance.onToggleSelection('programming');
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
        }

        it('renders the MANDATORY_FEATURE_MISSING violation with code, message, ids, relation, and suggestion', () => {
            disableProgrammingAndFlushViolation();

            const panel = rootEl(fixture).querySelector('[data-testid="violations-panel"]');
            expect(panel).not.toBeNull();
            const code = panel?.querySelector('[data-testid="violation-code"]');
            expect(code?.textContent).toBe('MANDATORY_FEATURE_MISSING');
            const message = panel?.querySelector('[data-testid="violation-message"]');
            expect(message?.textContent).toContain('mandatory under Exercise System');
            const relation = panel?.querySelector('[data-testid="violation-relation"]');
            expect(relation?.textContent).toContain('Exercise System');
            expect(relation?.textContent).toContain('Programming');
            const suggestion = panel?.querySelector('[data-testid="violation-suggestion"]');
            expect(suggestion?.textContent).toContain('Enable Programming.');
        });

        it('passes violation ids to the diagram so the programming node receives the violation modifier', () => {
            disableProgrammingAndFlushViolation();

            const programmingNode = rootEl(fixture).querySelector('.diagram-node[data-feature-id="programming"]');
            expect(programmingNode?.classList.contains('diagram-node--violation')).toBe(true);
        });

        it('shows the violation on the focused feature inside the details panel', () => {
            disableProgrammingAndFlushViolation();

            fixture.componentInstance.onSelectFeature('programming');
            fixture.detectChanges();
            const detailsViolations = rootEl(fixture).querySelector('[data-testid="details-violations"]');
            expect(detailsViolations?.textContent).toContain('MANDATORY_FEATURE_MISSING');
            expect(detailsViolations?.textContent).toContain('Enable Programming.');
        });

        it('renders the warnings panel and applies the warning modifier to listed feature nodes', () => {
            loadModelAndValidate(fixture, httpMock);

            fixture.componentInstance.onToggleSelection('iris');
            flushValidation(httpMock, {
                valid: true,
                normalizedSelection: [],
                violations: [],
                warnings: [
                    {
                        code: 'UNSUPPORTED_EXPRESSION_CONSTRAINT',
                        message: 'Iris is gated by an unsupported expression.',
                        featureIds: ['iris'],
                        constraintId: 'iris-expression',
                        suggestion: 'Review constraint manually.',
                    },
                ],
            });
            fixture.detectChanges();

            const warningsPanel = rootEl(fixture).querySelector('[data-testid="warnings-panel"]');
            expect(warningsPanel).not.toBeNull();
            expect(warningsPanel?.textContent).toContain('UNSUPPORTED_EXPRESSION_CONSTRAINT');
            const irisNode = rootEl(fixture).querySelector('.diagram-node[data-feature-id="iris"]');
            expect(irisNode?.classList.contains('diagram-node--warning')).toBe(true);
        });

        it('does not render the violations panel when the result is valid', () => {
            loadModelAndValidate(fixture, httpMock);
            expect(rootEl(fixture).querySelector('[data-testid="violations-panel"]')).toBeNull();
            expect(rootEl(fixture).querySelector('[data-testid="warnings-panel"]')).toBeNull();
        });
    });

    describe('search and expansion', () => {
        function typeSearch(value: string): void {
            const input = rootEl(fixture).querySelector('[data-testid="search-input"]') as HTMLInputElement;
            input.value = value;
            input.dispatchEvent(new Event('input'));
            fixture.detectChanges();
        }

        it('filters and highlights the diagram by feature name', () => {
            loadModelAndValidate(fixture, httpMock);
            typeSearch('lecture');

            const match = rootEl(fixture).querySelector('[data-testid="match-count"]');
            expect(match?.textContent?.trim()).toBe('1 match');

            const lectureNode = rootEl(fixture).querySelector('.diagram-node[data-feature-id="lecture"]');
            expect(lectureNode?.classList.contains('diagram-node--match')).toBe(true);
            expect(rootEl(fixture).querySelector('.diagram-node[data-feature-id="iris"]')).toBeNull();
        });

        it('filters the diagram by feature id case-insensitively', () => {
            loadModelAndValidate(fixture, httpMock);
            typeSearch('FILE-UPLOAD');

            const fileUpload = rootEl(fixture).querySelector('.diagram-node[data-feature-id="file-upload"]');
            expect(fileUpload?.classList.contains('diagram-node--match')).toBe(true);
        });

        it('clears the search and restores the full diagram', () => {
            loadModelAndValidate(fixture, httpMock);
            typeSearch('iris');
            const clearButton = rootEl(fixture).querySelector('[data-testid="clear-search"]') as HTMLButtonElement;
            clearButton.click();
            fixture.detectChanges();

            expect(rootEl(fixture).querySelector('[data-testid="match-count"]')).toBeNull();
            expect(fixture.componentInstance.searchQuery()).toBe('');
        });

        it('expand all renders every diagram node, and collapse all returns to the root branch', () => {
            loadModelAndValidate(fixture, httpMock);

            (rootEl(fixture).querySelector('[data-testid="expand-all"]') as HTMLButtonElement).click();
            fixture.detectChanges();
            const allNodes = rootEl(fixture).querySelectorAll('.diagram-node');
            expect(allNodes).toHaveLength(24);

            (rootEl(fixture).querySelector('[data-testid="collapse-all"]') as HTMLButtonElement).click();
            fixture.detectChanges();
            const collapsed = rootEl(fixture).querySelectorAll('.diagram-node');
            // Root + 5 collapsed group children
            expect(collapsed).toHaveLength(6);
        });

        it('does not render a view-mode toggle or list view', () => {
            loadModelAndValidate(fixture, httpMock);
            expect(rootEl(fixture).querySelector('[data-testid="view-list"]')).toBeNull();
            expect(rootEl(fixture).querySelector('[data-testid="view-diagram"]')).toBeNull();
            expect(rootEl(fixture).querySelector('fm-feature-model-tree-node')).toBeNull();
        });
    });
});
