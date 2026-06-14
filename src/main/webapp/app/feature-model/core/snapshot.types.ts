/**
 * Wire-format types for the local feature model snapshot API exposed under
 * `/api/feature-model/snapshots`. Snapshot metadata is advanced/maintainer information and is shown
 * in the Explorer, not in the guided Configurator.
 */

export interface SnapshotSummary {
    snapshotId: string;
    modelId: string | null;
    version: string | null;
    status: string | null;
    sourceRepo: string | null;
    sourceRef: string | null;
    sourceCommit: string | null;
    extractorVersion: string | null;
    active: boolean;
}
