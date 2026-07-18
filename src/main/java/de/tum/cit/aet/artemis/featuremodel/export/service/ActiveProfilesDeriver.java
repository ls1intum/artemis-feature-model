package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Derives the Spring {@code ACTIVE_PROFILES} string for the dev-ide run configuration from the feature selection.
 *
 * <p>
 * The rule set is data, not logic: a fixed base profile list for a local IDE development run, plus the local-CI
 * profile family if and only if a CI-dependent feature is selected. The CI rule reuses the Phase 6 finding that
 * Hyperion (like Programming) hard-requires a CI trigger bean at runtime, so a selection containing such a feature
 * must start the {@code localci}/{@code localvc}/{@code buildagent} profiles.
 *
 * <p>
 * Seam note: generated feature models since Phase E3 declare {@code SPRING_PROFILES_ACTIVE} token contributions as
 * artifact mappings on technical features. The bundled curated model carries no technical features, so this class
 * derives the profiles by rule; it is the seam where mapping-driven derivation will later replace the rule for models
 * that carry technical features. That consumption path is deliberately not built yet.
 */
@Component
public class ActiveProfilesDeriver {

    /** Base Spring profiles of a local Artemis IDE development run. */
    private static final List<String> BASE_PROFILES = List.of("artemis", "core", "dev", "local", "scheduling");

    /** Feature ids that require a CI trigger bean at runtime and therefore force the local-CI profile family. */
    private static final Set<String> CI_DEPENDENT_FEATURE_IDS = Set.of("programming", "hyperion");

    /** Spring profiles of the local-CI family, appended when a CI-dependent feature is selected. */
    private static final List<String> CI_PROFILES = List.of("localci", "localvc", "buildagent");

    /**
     * Derives the comma-separated {@code ACTIVE_PROFILES} value for a selection.
     *
     * @param selectedFeatureIds selected feature ids.
     * @return comma-separated Spring profile list, deterministic for the same selection.
     */
    public String deriveActiveProfiles(Collection<String> selectedFeatureIds) {
        List<String> profiles = new ArrayList<>(BASE_PROFILES);
        boolean ciDependentFeatureSelected = selectedFeatureIds.stream().anyMatch(CI_DEPENDENT_FEATURE_IDS::contains);
        if (ciDependentFeatureSelected) {
            profiles.addAll(CI_PROFILES);
        }
        return String.join(",", profiles);
    }
}
