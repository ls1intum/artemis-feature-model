import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { FeatureModelService } from '../api/feature-model.service';
import { SnapshotSummary } from '../core/snapshot.types';

@Component({
    selector: 'fm-feature-model-snapshots',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    templateUrl: './feature-model-snapshots.component.html',
    styleUrl: './feature-model-snapshots.component.scss',
})
export class FeatureModelSnapshotsComponent implements OnInit {
    private readonly featureModelService = inject(FeatureModelService);
    private readonly destroyRef = inject(DestroyRef);

    readonly snapshots = signal<SnapshotSummary[]>([]);
    readonly failed = signal<boolean>(false);
    readonly hasSnapshots = computed(() => this.snapshots().length > 0);

    ngOnInit(): void {
        this.featureModelService
            .loadSnapshots()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (snapshots) => this.snapshots.set(snapshots),
                error: () => this.failed.set(true),
            });
    }
}
