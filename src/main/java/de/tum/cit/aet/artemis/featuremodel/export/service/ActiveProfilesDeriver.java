package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Derives the Spring {@code ACTIVE_PROFILES} string for the dev-ide run configuration from the feature selection.
 *
 * <p>
 * The rule set is data, not logic: one fixed ordered profile list for a local IDE development run, and one for
 * selections containing a CI-dependent feature. The CI rule reuses the Phase 6 finding that Hyperion (like
 * Programming) hard-requires a CI trigger bean at runtime, so a selection containing such a feature must start the
 * {@code localci}/{@code localvc}/{@code buildagent} profiles.
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
 * Seam note: generated feature models since Phase E3 declare {@code SPRING_PROFILES_ACTIVE} token contributions as
 * artifact mappings on technical features. The bundled curated model carries no technical features, so this class
 * derives the profiles by rule; it is the seam where mapping-driven derivation will later replace the rule for models
 * that carry technical features. That consumption path is deliberately not built yet.
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

    /**
     * Derives the comma-separated {@code ACTIVE_PROFILES} value for a selection.
     *
     * @param selectedFeatureIds selected feature ids.
     * @return comma-separated Spring profile list, deterministic for the same selection.
     */
    public String deriveActiveProfiles(Collection<String> selectedFeatureIds) {
        boolean ciDependentFeatureSelected = selectedFeatureIds.stream().anyMatch(CI_DEPENDENT_FEATURE_IDS::contains);
        return String.join(",", ciDependentFeatureSelected ? CI_RUN_PROFILES : BASE_RUN_PROFILES);
    }
}
