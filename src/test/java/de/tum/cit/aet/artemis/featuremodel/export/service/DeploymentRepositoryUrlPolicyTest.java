package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentRepositoryPublishException;

/** Policy table for effective deployment repository URLs: fallback, trimming, allowed prefixes, and every rejection. */
class DeploymentRepositoryUrlPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = { "https://github.com/owner/repository.git", "https://github.com/owner/repository", "git@github.com:owner/repository.git",
            "ssh://git@github.com/owner/repository.git" })
    void acceptsGitHubOverHttpsAndBothSshForms(String url) {
        assertThat(DeploymentRepositoryUrlPolicy.effectiveUrl(url, properties(null, null))).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://github.com.evil.com/x/y", "https://token@github.com/x/y", "https://x-access-token:secret@github.com/x/y",
            "http://github.com/x/y", "file:///tmp/x", "https://gitlab.com/x/y", "https://github.com/", "git@github.com:", "https://github.com/x/../y",
            "git@github.com:../y", "ssh://git@github.com/x/..", "https://github.com/x/y?ref=main", "https://github.com/x/y#main",
            "https://github.com/x y", "https://github.com/x/\ty", "https://github.com/x/y\u0000", "https://github.com/x y" })
    void rejectsEveryUrlOutsideThePolicyAndListsTheAllowedPrefixes(String url) {
        assertThatThrownBy(() -> DeploymentRepositoryUrlPolicy.effectiveUrl(url, properties(null, null)))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("PUBLISH_REPOSITORY_NOT_ALLOWED");
                    assertThat(exception.getStatus().value()).isEqualTo(400);
                    assertThat(exception.getMessage()).contains("https://github.com/, git@github.com:, ssh://git@github.com/");
                });
    }

    @Test
    void tokenInUserInformationNeverReachesTheMessage() {
        assertThatThrownBy(() -> DeploymentRepositoryUrlPolicy.effectiveUrl("https://ghp_secretvalue@github.com/x/y", properties(null, null)))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                        exception -> assertThat(exception.getMessage()).contains("user information").doesNotContain("ghp_secretvalue"));
    }

    @Test
    void fileUrlsNeedAnExplicitlyAllowedPrefix() {
        DeploymentRepositoryProperties fileAllowed = properties(null, List.of("file:///tmp/deployments/"));

        assertThat(DeploymentRepositoryUrlPolicy.effectiveUrl("file:///tmp/deployments/repo.git", fileAllowed)).isEqualTo("file:///tmp/deployments/repo.git");
        assertThatThrownBy(() -> DeploymentRepositoryUrlPolicy.effectiveUrl("file:///tmp/other/repo.git", fileAllowed))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PUBLISH_REPOSITORY_NOT_ALLOWED"));
        assertThatThrownBy(() -> DeploymentRepositoryUrlPolicy.effectiveUrl("https://github.com/x/y", fileAllowed))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                        exception -> assertThat(exception.getMessage()).contains("file:///tmp/deployments/"));
    }

    @Test
    void blankRequestUrlFallsBackToTheTrimmedDefaultAndARequestUrlWins() {
        DeploymentRepositoryProperties withDefault = properties(" https://github.com/owner/default.git ", null);

        assertThat(DeploymentRepositoryUrlPolicy.effectiveUrl(null, withDefault)).isEqualTo("https://github.com/owner/default.git");
        assertThat(DeploymentRepositoryUrlPolicy.effectiveUrl("  ", withDefault)).isEqualTo("https://github.com/owner/default.git");
        assertThat(DeploymentRepositoryUrlPolicy.effectiveUrl(" https://github.com/owner/requested.git\n", withDefault))
                .isEqualTo("https://github.com/owner/requested.git");
    }

    @Test
    void theConfiguredDefaultPassesThePolicyToo() {
        assertThatThrownBy(() -> DeploymentRepositoryUrlPolicy.effectiveUrl(null, properties("http://github.com/owner/default.git", null)))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PUBLISH_REPOSITORY_NOT_ALLOWED"));
    }

    @Test
    void missingRequestAndDefaultUrlIsAControlledBadRequest() {
        for (String defaultUrl : Arrays.asList(null, "", "  ")) {
            assertThatThrownBy(() -> DeploymentRepositoryUrlPolicy.effectiveUrl(" ", properties(defaultUrl, null)))
                    .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class, exception -> {
                        assertThat(exception.getCode()).isEqualTo("PUBLISH_REQUIRES_REPOSITORY_URL");
                        assertThat(exception.getStatus().value()).isEqualTo(400);
                    });
        }
    }

    @Test
    void absentEmptyOrBlankPrefixListsFallBackToGitHub() {
        assertThat(properties(null, null).allowedRepositoryUrlPrefixes()).isEqualTo(DeploymentRepositoryProperties.DEFAULT_ALLOWED_REPOSITORY_URL_PREFIXES);
        assertThat(properties(null, List.of()).allowedRepositoryUrlPrefixes()).isEqualTo(DeploymentRepositoryProperties.DEFAULT_ALLOWED_REPOSITORY_URL_PREFIXES);
        assertThat(properties(null, List.of(" ", "")).allowedRepositoryUrlPrefixes())
                .isEqualTo(DeploymentRepositoryProperties.DEFAULT_ALLOWED_REPOSITORY_URL_PREFIXES);
        assertThat(properties(null, List.of(" ", "https://github.com/ls1intum/")).allowedRepositoryUrlPrefixes()).containsExactly("https://github.com/ls1intum/");
    }

    private DeploymentRepositoryProperties properties(String defaultUrl, List<String> allowedPrefixes) {
        return new DeploymentRepositoryProperties(true, defaultUrl, allowedPrefixes, "deployment", null, null, null, null);
    }
}
