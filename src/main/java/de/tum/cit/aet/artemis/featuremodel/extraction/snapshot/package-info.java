/**
 * Snapshot publication and consumption of the extraction pipeline: atomic fail-closed publication behind
 * {@code PackageStageService}, the read-only complete-bundle validator, controlled Docker context staging, and
 * repository provenance resolution. The bundle layout itself is the {@code SnapshotBundleContract} in
 * {@code extraction.domain}, shared with the runtime loader. Depends on {@code extraction.pipeline},
 * {@code extraction.report}, {@code extraction.domain}, {@code extraction.artifact}, and the runtime contract types
 * in {@code catalog}, {@code export}, and {@code selection}; never on the scan, model, or workflow packages.
 */
package de.tum.cit.aet.artemis.featuremodel.extraction.snapshot;
