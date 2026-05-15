package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

import java.util.List;

public record FeatureSource(String configKey, String springProfile, String frontendConstant, String backendConditionClass, List<String> evidence) {

    public FeatureSource {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
