/**
 * Feature extraction pipeline: scans a local Artemis checkout for variability anchors, produces namespaced feature
 * candidates with evidence, and generates a drift report against the active curated feature model. The pipeline is
 * invoked through the {@code extractFeatureModel} Gradle task and deliberately runs without a Spring context; no
 * extraction trigger is reachable by regular application users.
 */
package de.tum.cit.aet.artemis.featuremodel.extraction;
