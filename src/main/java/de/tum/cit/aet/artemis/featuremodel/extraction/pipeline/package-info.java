/**
 * Shared run-orchestration infrastructure of the staged extraction pipeline: the manifest-bound run context, verified
 * input loading and source preflight, manifest loading and preflight, the digest-verified stage artifact store, the
 * controlled-failure report path, and the invocation-only stage outcome records. This package must stay limited to
 * run-orchestration boundaries — discovery, model, workflow, and publication rules belong to the capability packages.
 * It may depend on {@code extraction.report}, {@code extraction.domain}, {@code extraction.artifact},
 * {@code extraction.repository}, and {@code extraction.source}, and never on a capability package, so no package
 * cycle can form. These classes are plain Java because extraction runs without a Spring context.
 */
package de.tum.cit.aet.artemis.featuremodel.extraction.pipeline;
