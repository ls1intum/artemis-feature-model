/**
 * Extraction algorithms and staged command orchestration. Package-private scanners emit raw facts; the stateless
 * candidate facade coordinates cohesive module, relation, runtime-toggle, deployment, and config-key assemblers.
 * Stage services obtain one manifest-bound run context, apply fail-soft discovery or fail-closed publication rules,
 * and use the artifact store for verified envelopes. Source location belongs to {@code extraction.source}, persisted
 * records to {@code extraction.domain}, and shared byte/digest/directory mechanics to {@code extraction.artifact}.
 * These classes are plain Java because extraction runs without a Spring context.
 */
package de.tum.cit.aet.artemis.featuremodel.extraction.service;
