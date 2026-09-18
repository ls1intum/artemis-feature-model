import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { LocalizedFeatureRef } from './configurator-view.types';

/** The fields a violation and a warning share; both render as one labelled issue card. */
export interface ValidationIssue {
    code: string;
    message: string;
    features: LocalizedFeatureRef[];
    suggestion: string | null;
}

export type ValidationIssueSeverity = 'blocking' | 'warning';

/**
 * Renders validation issues as labelled cards: a severity tag and the stable code in the head, then one row each
 * for the problem statement, the affected features, and the suggested fix. The labels give every line a name so a
 * reader can jump to the part they need instead of parsing one block of prose.
 */
@Component({
    selector: 'fm-validation-issue-list',
    standalone: true,
    imports: [],
    changeDetection: ChangeDetectionStrategy.OnPush,
    templateUrl: './validation-issue-list.component.html',
    styleUrl: './validation-issue-list.component.scss',
})
export class ValidationIssueListComponent {
    readonly issues = input.required<readonly ValidationIssue[]>();
    readonly severity = input.required<ValidationIssueSeverity>();
    /** Optional `data-testid` for each rendered item, so host specs keep their existing selectors. */
    readonly itemTestId = input<string | undefined>(undefined);

    readonly severityLabel = computed(() => (this.severity() === 'blocking' ? 'Blocking' : 'Warning'));
    readonly isBlocking = computed(() => this.severity() === 'blocking');
}
