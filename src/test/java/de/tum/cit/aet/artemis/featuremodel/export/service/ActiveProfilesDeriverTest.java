package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ActiveProfilesDeriverTest {

    private static final String BASE_PROFILES = "artemis,scheduling,core,dev,local";

    /**
     * The exact order of the shipped {@code Artemis_Server__Dev__BuildAgent_LocalCI_.xml}. The order is semantic:
     * {@code buildagent} must stay before {@code core}, because {@code application-buildagent.yml} excludes the
     * JPA/DataSource auto-configurations and only the later {@code application-core.yml} exclude list restores them.
     */
    private static final String CI_PROFILES = "artemis,localci,localvc,scheduling,buildagent,core,dev,local";

    private final ActiveProfilesDeriver deriver = new ActiveProfilesDeriver();

    @Test
    void derivesTheBaseProfilesForASelectionWithoutCiDependentFeatures() {
        String profiles = deriver.deriveActiveProfiles(Set.of("course-workflow", "communication", "quiz", "lecture"));

        assertThat(profiles).isEqualTo(BASE_PROFILES);
    }

    @Test
    void derivesTheCiProfilesWhenProgrammingIsSelected() {
        String profiles = deriver.deriveActiveProfiles(Set.of("course-workflow", "programming"));

        assertThat(profiles).isEqualTo(CI_PROFILES);
    }

    @Test
    void derivesTheCiProfilesWhenHyperionIsSelectedWithoutProgramming() {
        String profiles = deriver.deriveActiveProfiles(Set.of("course-workflow", "hyperion"));

        assertThat(profiles).isEqualTo(CI_PROFILES);
    }

    @Test
    void derivesTheCiProfilesOnlyOnceWhenBothCiDependentFeaturesAreSelected() {
        String profiles = deriver.deriveActiveProfiles(Set.of("programming", "hyperion"));

        assertThat(profiles).isEqualTo(CI_PROFILES);
    }

    @Test
    void derivesTheBaseProfilesForAnEmptySelection() {
        String profiles = deriver.deriveActiveProfiles(Set.of());

        assertThat(profiles).isEqualTo(BASE_PROFILES);
    }

    @Test
    void keepsBuildagentBeforeCoreSoJpaAutoConfigurationSurvives() {
        String profiles = deriver.deriveActiveProfiles(Set.of("programming"));

        assertThat(profiles.indexOf("buildagent")).isLessThan(profiles.indexOf("core"));
    }
}
