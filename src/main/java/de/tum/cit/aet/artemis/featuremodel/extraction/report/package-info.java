/**
 * Extraction report assembly and dependency-free HTML rendering. Both classes are public boundaries: the pipeline
 * artifact store renders {@code index.html} and the failure path plus the model and snapshot stages assemble the
 * consolidated report. This package depends only on {@code extraction.domain} and must never depend back on an
 * implementation package.
 */
package de.tum.cit.aet.artemis.featuremodel.extraction.report;
