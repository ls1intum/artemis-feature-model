/**
 * Extraction pipeline services: anchor scans over the Artemis checkout, candidate assembly, the drift comparison
 * against the active curated model, and deterministic output writing. These classes are plain Java on purpose; the
 * pipeline must run without a Spring context.
 */
package de.tum.cit.aet.artemis.featuremodel.extraction.service;
