package de.tum.cit.aet.artemis.featuremodel.catalog.dto;

import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureSource;

public record FeatureSourceDTO(String configKey, String springProfile, String frontendConstant, String backendConditionClass,
        List<String> evidence) {

    public FeatureSourceDTO {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public static FeatureSourceDTO fromDomain(FeatureSource source) {
        if (source == null) {
            return null;
        }
        return new FeatureSourceDTO(source.configKey(), source.springProfile(), source.frontendConstant(), source.backendConditionClass(),
                source.evidence());
    }
}
