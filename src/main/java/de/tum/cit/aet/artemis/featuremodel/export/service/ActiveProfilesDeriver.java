package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;

/**
 * Derives the Spring {@code ACTIVE_PROFILES} string for the dev-ide run configuration from the feature selection.
 *
 * <p>
 * The rule set is data, not logic: technical mappings select a complete ICL or Jenkins list. The curated-model
 * fallback retains one base list and one list for selections containing a CI-dependent feature, because Hyperion
 * (like Programming) hard-requires a CI trigger bean at runtime.
 *
 * <p>
 * The profile <em>order</em> is semantic, not cosmetic, and mirrors the run configurations the Artemis repository
 * ships: later profiles win for profile-specific config files, and list properties such as
 * {@code spring.autoconfigure.exclude} are replaced wholesale, never merged. In particular {@code buildagent} must
 * stay before {@code core}: {@code application-buildagent.yml} excludes the JPA/DataSource auto-configurations, and
 * only the later {@code application-core.yml} exclude list restores them — reversing that order leaves the
 * {@code entityManagerFactory} bean uncreated and Artemis fails to start.
 *
 * <p>
 * The extra {@link #FEATURE_MODEL_PROFILE} makes Spring Boot load the generated overlay directly: the overlay file is
 * named {@code application-feature-model.yml}, so activating a {@code feature-model} profile loads it as
 * profile-specific configuration once it is copied — under its original name — into the Artemis checkout's
 * {@code src/main/resources/config/} directory. It sits before {@code local} so a developer's
 * {@code application-local.yml} keeps the final say for machine-specific settings.
 *
 * <p>
 * The extra {@link #DEMO_ENV_PROFILE} loads {@code application-feature-model-demo.yml}, the dev-ide counterpart of
 * the local-docker package's {@code env/.env.demo}: dummy defaults for the {@code ${VARIABLE}} placeholders the
 * overlay references, so a DEMO run starts without manual environment setup. Real environment variables rank above
 * config files in Spring Boot's property precedence and therefore override the dummies automatically.
 *
 * <p>
 * Generated models choose one complete ordered list through their technical profile-token mappings. The bundled
 * curated model carries no technical mappings and keeps the original feature-rule fallback.
 */
@Component
public class ActiveProfilesDeriver {

    /** Profile that makes Spring load the generated {@code application-feature-model.yml} overlay by file name. */
    static final String FEATURE_MODEL_PROFILE = "feature-model";

    /** Profile that loads the generated demo defaults for the overlay's environment-variable placeholders. */
    static final String DEMO_ENV_PROFILE = "feature-model-demo";

    /** Feature ids that require a CI trigger bean at runtime and therefore force the local-CI profile family. */
    private static final Set<String> CI_DEPENDENT_FEATURE_IDS = Set.of("programming", "hyperion");

    /** Ordered profiles of an IDE development run without CI-dependent features, mirroring Artemis' run configs. */
    private static final List<String> BASE_RUN_PROFILES = List.of("artemis", "scheduling", "core", "dev", FEATURE_MODEL_PROFILE, DEMO_ENV_PROFILE, "local");

    /**
     * Ordered profiles when a CI-dependent feature is selected, mirroring the shipped
     * {@code Artemis_Server__Dev__BuildAgent_LocalCI_.xml}; {@code buildagent} must stay before {@code core} (see
     * class javadoc).
     */
    private static final List<String> CI_RUN_PROFILES = List.of("artemis", "localci", "localvc", "scheduling", "buildagent", "core", "dev", FEATURE_MODEL_PROFILE,
            DEMO_ENV_PROFILE, "local");

    /** Ordered profiles mirrored from {@code Artemis_Server__Dev__Core__Jenkins_.xml}. */
    private static final List<String> JENKINS_RUN_PROFILES = List.of("jenkins", "localvc", "artemis", "scheduling", "core", "dev", FEATURE_MODEL_PROFILE,
            DEMO_ENV_PROFILE, "local");

    private static final Set<String> ICL_TOKENS = Set.of("localci", "buildagent", "localvc");

    private static final Set<String> JENKINS_TOKENS = Set.of("jenkins", "localvc");

    /**
     * Derives the comma-separated {@code ACTIVE_PROFILES} value for a selection.
     *
     * @param selectedFeatureIds selected feature ids.
     * @return comma-separated Spring profile list, deterministic for the same selection.
     */
    public String deriveActiveProfiles(Collection<String> selectedFeatureIds) {
        return deriveActiveProfiles(selectedFeatureIds, List.of());
    }

    /**
     * Derives active profiles from resolved technical tokens, falling back to the curated-model feature rule when no
     * token mapping is present.
     *
     * @param selectedFeatureIds selected feature ids used by the curated-model fallback.
     * @param technicalProfileTokens resolved technical profile-token contributions.
     * @return comma-separated ordered Spring profiles.
     * @throws ArtifactGenerationException if the technical token set is unknown or mixes CI families.
     */
    public String deriveActiveProfiles(Collection<String> selectedFeatureIds, Collection<String> technicalProfileTokens) {
        Set<String> tokens = new LinkedHashSet<>(technicalProfileTokens);
        if (tokens.isEmpty()) {
            return fallbackProfiles(selectedFeatureIds);
        }
        if (ICL_TOKENS.equals(tokens)) {
            return String.join(",", CI_RUN_PROFILES);
        }
        if (JENKINS_TOKENS.equals(tokens)) {
            return String.join(",", JENKINS_RUN_PROFILES);
        }
        throw ArtifactGenerationException.unsupportedTechnicalProfileTokens(tokens);
    }

    /**
     * Applies the pre-technical-model rule for the curated model.
     *
     * @param selectedFeatureIds selected feature ids.
     * @return base or ICL profile list.
     */
    private String fallbackProfiles(Collection<String> selectedFeatureIds) {
        boolean ciDependentFeatureSelected = selectedFeatureIds.stream().anyMatch(CI_DEPENDENT_FEATURE_IDS::contains);
        return String.join(",", ciDependentFeatureSelected ? CI_RUN_PROFILES : BASE_RUN_PROFILES);
    }
}
