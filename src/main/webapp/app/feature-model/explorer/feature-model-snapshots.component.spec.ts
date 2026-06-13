import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { FeatureModelService } from '../api/feature-model.service';
import { SnapshotSummary } from '../core/snapshot.types';
import { FeatureModelSnapshotsComponent } from './feature-model-snapshots.component';

function snapshot(overrides: Partial<SnapshotSummary> = {}): SnapshotSummary {
    return {
        snapshotId: 'develop-latest',
        modelId: 'artemis-feature-model',
        version: '1.0.0',
        status: 'development',
        sourceRepo: 'ls1intum/Artemis',
        sourceRef: 'develop',
        sourceCommit: 'abc123',
        extractorVersion: 'feature-model-extractor@0.2.0',
        active: false,
        ...overrides,
    };
}

function configure(loadSnapshots: ReturnType<typeof vi.fn>): ComponentFixture<FeatureModelSnapshotsComponent> {
    TestBed.configureTestingModule({
        imports: [FeatureModelSnapshotsComponent],
        providers: [{ provide: FeatureModelService, useValue: { loadSnapshots } }],
    });
    return TestBed.createComponent(FeatureModelSnapshotsComponent);
}

describe('FeatureModelSnapshotsComponent', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('shows the classpath fallback message when no snapshots are imported', () => {
        const fixture = configure(vi.fn(() => of([])));
        fixture.detectChanges();

        const empty = fixture.nativeElement.querySelector('[data-testid="snapshot-empty"]');
        expect(empty).not.toBeNull();
        expect(empty?.textContent).toContain('bootstrap classpath model');
    });

    it('lists imported snapshots and marks the active one', () => {
        const fixture = configure(vi.fn(() => of([snapshot({ snapshotId: 'develop-latest', active: true }), snapshot({ snapshotId: 'release-1', active: false })])));
        fixture.detectChanges();

        const items = fixture.nativeElement.querySelectorAll('[data-testid="snapshot-item"]');
        expect(items).toHaveLength(2);
        expect((items[0] as HTMLElement).textContent).toContain('develop-latest');
        expect((items[1] as HTMLElement).textContent).toContain('release-1');
        expect(fixture.nativeElement.querySelector('[data-testid="snapshot-active"]')).not.toBeNull();
    });

    it('shows an unavailable message when the snapshot request fails', () => {
        const fixture = configure(vi.fn(() => throwError(() => new Error('boom'))));
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="snapshot-error"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="snapshot-empty"]')).toBeNull();
    });
});
