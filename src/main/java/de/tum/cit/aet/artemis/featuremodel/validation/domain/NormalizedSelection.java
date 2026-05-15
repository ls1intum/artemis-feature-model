package de.tum.cit.aet.artemis.featuremodel.validation.domain;

import java.util.List;
import java.util.Set;

public record NormalizedSelection(List<String> selectedFeatureIds, Set<String> submittedFeatureIds) {

    public NormalizedSelection {
        selectedFeatureIds = selectedFeatureIds == null ? List.of() : List.copyOf(selectedFeatureIds);
        submittedFeatureIds = submittedFeatureIds == null ? Set.of() : Set.copyOf(submittedFeatureIds);
    }
}
