/**
 * Manifest-driven feature extraction commands. The scan command reads a verified local Artemis checkout and writes
 * stable source facts; model, workflow, and packaging commands consume digest-verified artifacts without reopening
 * that checkout. {@code buildFeatureModelSnapshot} composes the stages, while {@code extractFeatureModel} remains a
 * deprecated alias. The pipeline deliberately runs without a Spring context and is not reachable by application
 * users.
 */
package de.tum.cit.aet.artemis.featuremodel.extraction;
