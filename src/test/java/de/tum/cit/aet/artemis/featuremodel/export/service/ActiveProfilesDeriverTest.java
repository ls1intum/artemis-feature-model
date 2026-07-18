package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ActiveProfilesDeriverTest {

    private static final String BASE_PROFILES = "artemis,core,dev,local,scheduling";

    private static final String CI_PROFILES = "localci,localvc,buildagent";

    private final ActiveProfilesDeriver deriver = new ActiveProfilesDeriver();

    @Test
    void derivesTheBaseProfilesForASelectionWithoutCiDependentFeatures() {
        String profiles = deriver.deriveActiveProfiles(Set.of("course-workflow", "communication", "quiz", "lecture"));

        assertThat(profiles).isEqualTo(BASE_PROFILES);
    }

    @Test
    void appendsTheCiProfilesWhenProgrammingIsSelected() {
        String profiles = deriver.deriveActiveProfiles(Set.of("course-workflow", "programming"));

        assertThat(profiles).isEqualTo(BASE_PROFILES + "," + CI_PROFILES);
    }

    @Test
    void appendsTheCiProfilesWhenHyperionIsSelectedWithoutProgramming() {
        String profiles = deriver.deriveActiveProfiles(Set.of("course-workflow", "hyperion"));

        assertThat(profiles).isEqualTo(BASE_PROFILES + "," + CI_PROFILES);
    }

    @Test
    void appendsTheCiProfilesOnlyOnceWhenBothCiDependentFeaturesAreSelected() {
        String profiles = deriver.deriveActiveProfiles(Set.of("programming", "hyperion"));

        assertThat(profiles).isEqualTo(BASE_PROFILES + "," + CI_PROFILES);
    }

    @Test
    void derivesTheBaseProfilesForAnEmptySelection() {
        String profiles = deriver.deriveActiveProfiles(Set.of());

        assertThat(profiles).isEqualTo(BASE_PROFILES);
    }
}
