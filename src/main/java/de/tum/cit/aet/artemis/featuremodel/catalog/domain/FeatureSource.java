package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeatureSource(String configKey, String springProfile, String clientConstant, String serverConditionClass, String usageLabel,
        List<String> evidence) {

    /**
     * Creates feature source metadata and normalizes nullable evidence to an immutable empty list.
     *
     * @param configKey optional server configuration key.
     * @param springProfile optional Spring profile.
     * @param clientConstant optional client feature constant.
     * @param serverConditionClass optional server condition class.
     * @param usageLabel optional {@code @FeatureUsage} label of a sub-feature node.
     * @param evidence source evidence entries.
     */
    public FeatureSource {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    /**
     * Creates feature source metadata without a usage label, the shape of every node that is not a sub-feature.
     *
     * @param configKey optional server configuration key.
     * @param springProfile optional Spring profile.
     * @param clientConstant optional client feature constant.
     * @param serverConditionClass optional server condition class.
     * @param evidence source evidence entries.
     */
    public FeatureSource(String configKey, String springProfile, String clientConstant, String serverConditionClass, List<String> evidence) {
        this(configKey, springProfile, clientConstant, serverConditionClass, null, evidence);
    }
}
