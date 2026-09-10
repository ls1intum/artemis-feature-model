package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.ObjectMapper;

/** Tests GitHub visibility requests for SSH repository URLs without making network calls. */
class DeploymentRepositoryPublisherVisibilityTest {

    @ParameterizedTest
    @ValueSource(strings = { "ssh://git@github.com/example/deployments.git", "git@github.com:example/deployments.git" })
    void unauthenticatedGitHubNotFoundMeansPrivate(String repositoryUrl) {
        HttpResponse<String> response = response(404, "");
        CapturingPublisher publisher = new CapturingPublisher(properties(repositoryUrl), response, name -> null);

        String visibility = publisher.fetchGitHubVisibility();

        assertThat(visibility).isEqualTo("private");
        assertThat(publisher.request().uri().toString()).isEqualTo("https://api.github.com/repos/example/deployments");
        assertThat(publisher.request().headers().firstValue("Authorization")).isEmpty();
    }

    @Test
    void sshVisibilityUsesTheTokenWhenItIsPresent() {
        HttpResponse<String> response = response(200, "{\"visibility\":\"public\"}");
        UnaryOperator<String> environmentReader = name -> DeploymentRepositoryPublisher.TOKEN_ENV_VAR.equals(name) ? "token-marker" : null;
        CapturingPublisher publisher = new CapturingPublisher(properties("git@github.com:example/deployments.git"), response, environmentReader);

        String visibility = publisher.fetchGitHubVisibility();

        assertThat(visibility).isEqualTo("public");
        assertThat(publisher.request().headers().firstValue("Authorization")).contains("Bearer token-marker");
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    private DeploymentRepositoryProperties properties(String repositoryUrl) {
        return new DeploymentRepositoryProperties(true, repositoryUrl, "deployment", null, "private", null, null);
    }

    private static final class CapturingPublisher extends DeploymentRepositoryPublisher {

        private final HttpResponse<String> response;

        private HttpRequest request;

        private CapturingPublisher(DeploymentRepositoryProperties properties, HttpResponse<String> response,
                UnaryOperator<String> environmentReader) {
            super(properties, new ObjectMapper(), environmentReader);
            this.response = response;
        }

        @Override
        protected HttpResponse<String> sendGitHubVisibilityRequest(HttpRequest request) {
            this.request = request;
            return response;
        }

        private HttpRequest request() {
            return request;
        }
    }
}
