package de.tum.cit.aet.artemis.featuremodel.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EnvironmentVariableNamesTest {

    @Test
    void uppercasesAndReplacesSeparatorsWithUnderscores() {
        assertThat(EnvironmentVariableNames.derive("artemis.iris.url")).isEqualTo("ARTEMIS_IRIS_URL");
    }

    @Test
    void collapsesEveryNonAlphanumericRunToOneUnderscore() {
        assertThat(EnvironmentVariableNames.derive("artemis.continuous-integration.artemis-authentication-token-key"))
                .isEqualTo("ARTEMIS_CONTINUOUS_INTEGRATION_ARTEMIS_AUTHENTICATION_TOKEN_KEY");
        assertThat(EnvironmentVariableNames.derive("a..b--c")).isEqualTo("A_B_C");
    }

    @Test
    void trimsLeadingAndTrailingUnderscores() {
        assertThat(EnvironmentVariableNames.derive(".artemis.url.")).isEqualTo("ARTEMIS_URL");
    }

    @Test
    void rejectsAPathThatDerivesAnEmptyName() {
        assertThatThrownBy(() -> EnvironmentVariableNames.derive("...")).isInstanceOf(IllegalArgumentException.class);
    }
}
