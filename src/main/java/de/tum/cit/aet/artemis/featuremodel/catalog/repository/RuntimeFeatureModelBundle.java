package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GeneratedSnapshotMetadata;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;

/**
 * One immutable, validated runtime source for the model, workflow, and Artemis config-key catalog.
 *
 * @param model active feature model.
 * @param workflow active guided workflow.
 * @param catalog active Artemis config-key catalog.
 * @param provenance safe bundle identity.
 * @param snapshotMetadata generated snapshot metadata, or {@code null} in classpath mode.
 */
public record RuntimeFeatureModelBundle(FeatureModel model, GuidedWorkflow workflow, ArtemisConfigKeyCatalog catalog,
        RuntimeFeatureModelProvenance provenance, GeneratedSnapshotMetadata snapshotMetadata) {
}
