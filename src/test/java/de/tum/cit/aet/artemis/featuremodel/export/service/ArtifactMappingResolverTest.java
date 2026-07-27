package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ModelMetadata;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GenerationMessage;
import de.tum.cit.aet.artemis.featuremodel.export.domain.OverlayEntry;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ResolutionResult;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

class ArtifactMappingResolverTest {

    private final ArtifactMappingResolver resolver = new ArtifactMappingResolver(new ProfileParameterResolver());

    private FeatureModel model;

    @BeforeEach
    void setUp() {
        model = new JsonFeatureModelStore(new DefaultResourceLoader(), new ObjectMapper()).loadActiveModel();
    }

    @Test
    void writesUnselectedToggleFeaturesAsFalse() {
        ResolutionResult result = resolver.resolve(model, Set.of(), profile(Map.of()));

        assertThat(value(result, "artemis.iris.enabled")).isEqualTo(Boolean.FALSE);
        assertThat(value(result, "artemis.lecture.enabled")).isEqualTo(Boolean.FALSE);
        assertThat(entry(result, "artemis.iris.url")).isEmpty();
    }

    @Test
    void writesSelectedToggleAndConvertsEnvironmentSecret() {
        DeploymentProfile profile = profile(Map.of("artemis.iris.url", "https://pyris.example.com", "artemis.iris.secret-token", "env:ARTEMIS_IRIS_SECRET_TOKEN"));

        ResolutionResult result = resolver.resolve(model, Set.of("iris"), profile);

        assertThat(value(result, "artemis.iris.enabled")).isEqualTo(Boolean.TRUE);
        assertThat(value(result, "artemis.iris.url")).isEqualTo("https://pyris.example.com");
        assertThat(value(result, "artemis.iris.secret-token")).isEqualTo("${ARTEMIS_IRIS_SECRET_TOKEN}");
        assertThat(result.environmentVariables()).contains("ARTEMIS_IRIS_SECRET_TOKEN");
        assertThat(result.consumedParameters()).anySatisfy(consumed -> {
            assertThat(consumed.targetPath()).isEqualTo("artemis.iris.secret-token");
            assertThat(consumed.secret()).isTrue();
            assertThat(consumed.source()).isEqualTo("env");
        });
        assertThat(messages(result)).anyMatch(message -> message.contains("artemis.iris.url") && message.contains("Placeholder"));
    }

    @Test
    void reportsMissingRequiredProfileValue() {
        ResolutionResult result = resolver.resolve(model, Set.of("iris"), profile(Map.of()));

        assertThat(entry(result, "artemis.iris.url")).isEmpty();
        assertThat(result.omittedMappings()).anyMatch(omitted -> omitted.targetPath().equals("artemis.iris.url"));
        assertThat(messages(result)).anyMatch(message -> message.contains("artemis.iris.url") && message.contains("missing"));
    }

    @Test
    void refusesPlaintextSecretLiteral() {
        DeploymentProfile profile = profile(Map.of("artemis.iris.url", "http://localhost", "artemis.iris.secret-token", "plaintext-secret"));

        ResolutionResult result = resolver.resolve(model, Set.of("iris"), profile);

        assertThat(entry(result, "artemis.iris.secret-token")).isEmpty();
        assertThat(result.entries()).noneMatch(overlay -> "plaintext-secret".equals(overlay.value()));
        assertThat(result.omittedMappings()).anyMatch(omitted -> omitted.targetPath().equals("artemis.iris.secret-token"));
        assertThat(messages(result)).anyMatch(message -> message.contains("artemis.iris.secret-token") && message.contains("reference"));
    }

    @Test
    void omitsUnresolvedVaultReference() {
        DeploymentProfile profile = profile(Map.of("artemis.athena.url", "http://localhost:5100", "artemis.athena.secret", "vault:secret/artemis/athena#secret"));

        ResolutionResult result = resolver.resolve(model, Set.of("athena"), profile);

        assertThat(entry(result, "artemis.athena.secret")).isEmpty();
        assertThat(result.omittedMappings()).anyMatch(omitted -> omitted.targetPath().equals("artemis.athena.secret"));
    }

    @Test
    void warnsThatLtiNeedsManualRegistration() {
        ResolutionResult result = resolver.resolve(model, Set.of("lti"), profile(Map.of()));

        assertThat(result.messages()).anyMatch(message -> "lti".equals(message.featureId()) && message.message().toLowerCase().contains("registration"));
    }

    @Test
    void notesSelectedFeaturesWithoutMapping() {
        ResolutionResult result = resolver.resolve(model, Set.of("course-workflow"), profile(Map.of()));

        assertThat(result.messages()).anyMatch(message -> GenerationMessage.INFO.equals(message.severity()) && message.message().contains("Course Workflow"));
    }

    @Test
    void skipsOptionalMissingProfileValueSilently() {
        ResolutionResult result = resolver.resolve(model, Set.of("atlas"), profile(Map.of()));

        assertThat(value(result, "artemis.atlas.enabled")).isEqualTo(Boolean.TRUE);
        assertThat(result.omittedMappings()).noneMatch(omitted -> omitted.targetPath().equals("artemis.atlas.chat-model"));
        assertThat(messages(result)).noneMatch(message -> message.contains("artemis.atlas.chat-model"));
    }

    @Test
    void ignoresMappingsThatDoNotTargetTheOverlayFile() {
        List<ArtifactMapping> mappings = List.of(
                new ArtifactMapping("application-feature-model.yml", "artemis.demo.enabled", JsonNodeFactory.instance.booleanNode(true),
                        JsonNodeFactory.instance.booleanNode(false), null, null, null),
                new ArtifactMapping(".env", "DEMO_FLAG", JsonNodeFactory.instance.booleanNode(true), null, null, null, null),
                new ArtifactMapping(".env", "DEMO_SECRET", null, null, "demo.secret", true, true));
        FeatureNode feature = new FeatureNode("demo", "Demo", "feature", true, null, "disabled", null, null, List.of(), List.of(), List.of(), mappings, null);
        FeatureModel syntheticModel = new FeatureModel(new ModelMetadata("test-model", "Test Model", "1.0.0", "draft", null), List.of(feature), List.of(), List.of());

        ResolutionResult result = resolver.resolve(syntheticModel, Set.of("demo"), profile(Map.of("demo.secret", "env:DEMO_SECRET")));

        assertThat(result.entries()).extracting(OverlayEntry::path).containsExactly("artemis.demo.enabled");
        assertThat(result.environmentVariables()).isEmpty();
        assertThat(result.consumedParameters()).isEmpty();
        assertThat(result.omittedMappings()).isEmpty();
    }

    @Test
    void warnsAboutDeprecatedProfileKeys() {
        ResolutionResult result = resolver.resolve(model, Set.of(), profile(Map.of("pyris.url", "https://pyris.example.com")));

        assertThat(messages(result)).anyMatch(message -> message.contains("pyris.url") && message.contains("deprecated"));
    }

    private DeploymentProfile profile(Map<String, String> parameters) {
        return new DeploymentProfile("test-profile", "Test Profile", "1.0.0", "published", List.of(), List.of(), parameters, List.of());
    }

    private Optional<OverlayEntry> entry(ResolutionResult result, String path) {
        return result.entries().stream().filter(overlay -> overlay.path().equals(path)).findFirst();
    }

    private Object value(ResolutionResult result, String path) {
        return entry(result, path).map(OverlayEntry::value).orElse(null);
    }

    private List<String> messages(ResolutionResult result) {
        return result.messages().stream().map(GenerationMessage::message).toList();
    }
}
