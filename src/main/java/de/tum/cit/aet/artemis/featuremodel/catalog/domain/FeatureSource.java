package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

import java.util.List;

public record FeatureSource(String configKey, String springProfile, String frontendConstant, String backendConditionClass, List<String> evidence) {

    /**
     * Creates feature source metadata and normalizes nullable evidence to an immutable empty list.
     *
     * @param configKey optional backend configuration key.
     * @param springProfile optional Spring profile.
     * @param frontendConstant optional frontend feature constant.
     * @param backendConditionClass optional backend condition class.
     * @param evidence source evidence entries.
     */
    public FeatureSource {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
