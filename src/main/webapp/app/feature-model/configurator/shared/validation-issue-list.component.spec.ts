import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { ValidationIssue, ValidationIssueListComponent, ValidationIssueSeverity } from './validation-issue-list.component';

const REQUIRES_VIOLATION: ValidationIssue = {
    code: 'REQUIRES_CONSTRAINT_VIOLATED',
    message: "Feature 'apollon' requires feature 'modeling'.",
    features: [
        { id: 'apollon', name: 'Apollon (Modeling)' },
        { id: 'modeling', name: 'Modeling Exercises' },
    ],
    suggestion: "Enable 'modeling' or disable 'apollon'.",
};

const PLAIN_WARNING: ValidationIssue = {
    code: 'PROFILE_CAPABILITY_MISSING',
    message: 'The active profile does not provide iris-service.',
    features: [],
    suggestion: null,
};

function createFixture(issues: ValidationIssue[], severity: ValidationIssueSeverity, itemTestId?: string): ComponentFixture<ValidationIssueListComponent> {
    const fixture = TestBed.createComponent(ValidationIssueListComponent);
    fixture.componentRef.setInput('issues', issues);
    fixture.componentRef.setInput('severity', severity);
    if (itemTestId) {
        fixture.componentRef.setInput('itemTestId', itemTestId);
    }
    fixture.detectChanges();
    return fixture;
}

function rows(fixture: ComponentFixture<ValidationIssueListComponent>): Record<string, string> {
    const result: Record<string, string> = {};
    const terms = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('dt'));
    for (const term of terms) {
        const definition = term.nextElementSibling as HTMLElement | null;
        const chips = Array.from(definition?.querySelectorAll('.issue__feature') ?? []).map((chip) => chip.textContent?.trim() ?? '');
        result[term.textContent?.trim() ?? ''] = chips.length > 0 ? chips.join(', ') : (definition?.textContent?.trim() ?? '');
    }
    return result;
}

describe('ValidationIssueListComponent', () => {
    beforeEach(() => {
        TestBed.configureTestingModule({ imports: [ValidationIssueListComponent] });
    });

    it('labels the problem, the affected features, and the fix as separate rows', () => {
        const fixture = createFixture([REQUIRES_VIOLATION], 'blocking', 'guided-violation-item');
        const element = fixture.nativeElement as HTMLElement;

        expect(rows(fixture)).toEqual({
            Problem: "Feature 'apollon' requires feature 'modeling'.",
            Affects: 'Apollon (Modeling), Modeling Exercises',
            Fix: "Enable 'modeling' or disable 'apollon'.",
        });
        expect(element.querySelector('.issue__severity')?.textContent?.trim()).toBe('Blocking');
        expect(element.querySelector('.issue__code')?.textContent?.trim()).toBe('REQUIRES_CONSTRAINT_VIOLATED');
        expect(element.querySelector('.issue')?.classList.contains('issue--blocking')).toBe(true);
        expect(element.querySelector('[data-testid="guided-violation-item"]')).not.toBeNull();
        const chips = Array.from(element.querySelectorAll('.issue__feature')).map((chip) => chip.getAttribute('title'));
        expect(chips).toEqual(['apollon', 'modeling']);
    });

    it('omits the Affects and Fix rows when a warning carries neither', () => {
        const fixture = createFixture([PLAIN_WARNING], 'warning');
        const element = fixture.nativeElement as HTMLElement;

        expect(Object.keys(rows(fixture))).toEqual(['Problem']);
        expect(element.querySelector('.issue__severity')?.textContent?.trim()).toBe('Warning');
        expect(element.querySelector('.issue')?.classList.contains('issue--warning')).toBe(true);
        expect(element.querySelector('li[data-testid]')).toBeNull();
    });

    it('renders one card per issue in order', () => {
        const fixture = createFixture([REQUIRES_VIOLATION, { ...PLAIN_WARNING, code: 'MANDATORY_FEATURE_MISSING' }], 'blocking');
        const codes = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.issue__code')).map((code) => code.textContent?.trim());
        expect(codes).toEqual(['REQUIRES_CONSTRAINT_VIOLATED', 'MANDATORY_FEATURE_MISSING']);
    });
});
