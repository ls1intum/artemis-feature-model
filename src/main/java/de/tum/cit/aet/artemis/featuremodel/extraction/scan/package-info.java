/**
 * Source discovery of the extraction pipeline: the package-private Artemis source scanners, the stateless candidate,
 * evidence, and relation assemblers, and {@code ScanStageService} as the only public entry point. Scanners emit raw
 * facts fail-soft; the candidate facade coordinates cohesive module, relation, runtime-toggle, and deployment
 * assemblers. Configuration keys remain source evidence and generated-catalog inputs instead of becoming standalone
 * feature candidates. Depends on {@code extraction.pipeline}, {@code extraction.domain},
 * {@code extraction.repository}, and {@code extraction.source}; never on the model, workflow, report, or snapshot
 * packages.
 */
package de.tum.cit.aet.artemis.featuremodel.extraction.scan;
