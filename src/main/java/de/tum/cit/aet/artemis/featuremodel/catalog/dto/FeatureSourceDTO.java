package de.tum.cit.aet.artemis.featuremodel.catalog.dto;

import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureSource;

public record FeatureSourceDTO(String configKey, String springProfile, String clientConstant, String serverConditionClass,
        List<String> evidence) {

    /**
     * Creates a feature source DTO and normalizes nullable evidence to an immutable empty list.
     *
     * @param configKey optional server configuration key.
     * @param springProfile optional Spring profile.
     * @param clientConstant optional client feature constant.
     * @param serverConditionClass optional server condition class.
     * @param evidence source evidence entries.
     */
    public FeatureSourceDTO {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    /**
     * Converts domain source metadata to its REST DTO representation.
     *
     * @param source domain source metadata, or null if a feature has no source metadata.
     * @return DTO containing the same source data, or null when {@code source} is null.
     */
    public static FeatureSourceDTO fromDomain(FeatureSource source) {
        if (source == null) {
            return null;
        }
        return new FeatureSourceDTO(source.configKey(), source.springProfile(), source.clientConstant(), source.serverConditionClass(),
                source.evidence());
    }
}
