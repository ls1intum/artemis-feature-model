/**
 * Model generation of the extraction pipeline: manifest curation, manifest and generated-output conformance, the
 * generated feature model and config-key catalog assemblers, model-side validation, and {@code ModelStageService} as
 * the only public entry point. Curation is fail-closed — an incomplete manifest stops at the conformance gate with
 * diagnostics instead of assembling a model. Depends on {@code extraction.pipeline}, {@code extraction.report},
 * {@code extraction.domain}, and the runtime contract types in {@code catalog}, {@code deployment}, and
 * {@code export}; never on the scan, workflow, or snapshot packages.
 */
package de.tum.cit.aet.artemis.featuremodel.extraction.model;
