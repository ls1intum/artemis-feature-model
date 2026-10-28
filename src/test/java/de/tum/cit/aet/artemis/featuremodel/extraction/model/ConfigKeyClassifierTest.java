package de.tum.cit.aet.artemis.featuremodel.extraction.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Covers every placeholder pattern and lexicon rule of the derived-key classification. */
class ConfigKeyClassifierTest {

    @Test
    void everyEndpointAndCredentialLexiconSegmentMakesAKeyADeploymentInput() {
        for (String segment : ConfigKeyClassifier.ENDPOINT_LEXICON) {
            assertThat(ConfigKeyClassifier.isDeploymentInput("artemis.service." + segment, "https://real.example.org")).as(segment).isTrue();
        }
        for (String segment : ConfigKeyClassifier.CREDENTIAL_LEXICON) {
            assertThat(ConfigKeyClassifier.isDeploymentInput("artemis.service." + segment, null)).as(segment).isTrue();
        }
    }

    @Test
    void everyPlaceholderPatternMakesAKeyADeploymentInput() {
        assertThat(ConfigKeyClassifier.isPlaceholderLike("<your-pyris-url>")).isTrue();
        assertThat(ConfigKeyClassifier.isPlaceholderLike("your-account")).isTrue();
        assertThat(ConfigKeyClassifier.isPlaceholderLike("some-placeholder-value")).isTrue();
        assertThat(ConfigKeyClassifier.isPlaceholderLike("dummy-key")).isTrue();
        assertThat(ConfigKeyClassifier.isPlaceholderLike("changeme")).isTrue();
        assertThat(ConfigKeyClassifier.isPlaceholderLike("http://localhost:8000")).isTrue();
        assertThat(ConfigKeyClassifier.isPlaceholderLike("https://127.0.0.1/api")).isTrue();
        for (String sample : ConfigKeyClassifier.WELL_KNOWN_SAMPLE_SECRETS) {
            assertThat(ConfigKeyClassifier.isPlaceholderLike(sample)).as(sample).isTrue();
        }
        assertThat(ConfigKeyClassifier.isDeploymentInput("artemis.service.chat-model", "<placeholder>")).isTrue();
    }

    @Test
    void keysWithRealDefaultsAndNeutralLastSegmentsStayTunables() {
        assertThat(ConfigKeyClassifier.isDeploymentInput("artemis.atlas.chat-model", "gpt-4o")).isFalse();
        assertThat(ConfigKeyClassifier.isDeploymentInput("artemis.iris.health-ttl", 500L)).isFalse();
        assertThat(ConfigKeyClassifier.isDeploymentInput("artemis.service.enabled", Boolean.TRUE)).isFalse();
        assertThat(ConfigKeyClassifier.isDeploymentInput("artemis.service.mode", "https-in-the-middle.example.org")).isFalse();
        assertThat(ConfigKeyClassifier.isDeploymentInput("artemis.service.timeout", null)).isFalse();
    }

    @Test
    void credentialSegmentsAndSecretPlaceholdersClassifyAsSecrets() {
        assertThat(ConfigKeyClassifier.isSecret("artemis.iris.secret-token", "<your-secret>")).isTrue();
        assertThat(ConfigKeyClassifier.isSecret("artemis.service.token.timeout", null)).as("any segment matches, not only the last").isTrue();
        assertThat(ConfigKeyClassifier.isSecret("artemis.service.endpoint", "<your-secret>")).as("placeholder mentioning a secret").isTrue();
        assertThat(ConfigKeyClassifier.isSecret("artemis.service.endpoint", "<your-password>")).isTrue();
        assertThat(ConfigKeyClassifier.isSecret("artemis.service.url", "http://localhost:8000")).isFalse();
        assertThat(ConfigKeyClassifier.isSecret("artemis.service.endpoint", "secretive-but-real.example.org")).as("a real default never marks a secret")
                .isFalse();
    }
}
