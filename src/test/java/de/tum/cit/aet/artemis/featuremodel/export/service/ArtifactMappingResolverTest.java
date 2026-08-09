package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMappingSource;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ModelMetadata;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.export.domain.EnvironmentRequirement;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GenerationMessage;
import de.tum.cit.aet.artemis.featuremodel.export.domain.OverlayEntry;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ResolutionResult;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

public class ArtifactMappingResolverTest {

    private ArtifactMappingResolver resolver;

    private FeatureModel model;

    @BeforeEach
    void setUp() {
        model = new JsonFeatureModelStore(new DefaultResourceLoader(), new ObjectMapper()).loadActiveModel();
        resolver = new ArtifactMappingResolver(classpathCatalog());
    }

    @Test
    void writesUnselectedSelectionFeaturesAsFalse() {
        ResolutionResult result = resolver.resolve(model, Set.of());

        assertThat(value(result, "artemis.iris.enabled")).isEqualTo(Boolean.FALSE);
        assertThat(value(result, "artemis.lecture.enabled")).isEqualTo(Boolean.FALSE);
        assertThat(entry(result, "artemis.iris.url")).isEmpty();
        assertThat(result.environmentRequirements()).isEmpty();
    }

    @Test
    void writesEnvironmentMappingsAsDerivedPlaceholdersWithRequirements() {
        ResolutionResult result = resolver.resolve(model, Set.of("iris"));

        assertThat(value(result, "artemis.iris.enabled")).isEqualTo(Boolean.TRUE);
        assertThat(value(result, "artemis.iris.url")).isEqualTo("${ARTEMIS_IRIS_URL}");
        assertThat(value(result, "artemis.iris.secret-token")).isEqualTo("${ARTEMIS_IRIS_SECRET_TOKEN}");
        assertThat(result.environmentRequirements()).anySatisfy(requirement -> {
            assertThat(requirement.name()).isEqualTo("ARTEMIS_IRIS_URL");
            assertThat(requirement.featureId()).isEqualTo("iris");
            assertThat(requirement.configKey()).isEqualTo("artemis.iris.url");
            assertThat(requirement.catalogType()).isEqualTo(ArtemisConfigKeyCatalog.TYPE_URL);
            assertThat(requirement.secret()).isFalse();
            assertThat(requirement.source()).isEqualTo(EnvironmentRequirement.SOURCE_ARTIFACT_MAPPING);
        });
        assertThat(result.environmentRequirements()).anySatisfy(requirement -> {
            assertThat(requirement.name()).isEqualTo("ARTEMIS_IRIS_SECRET_TOKEN");
            assertThat(requirement.secret()).isTrue();
        });
        assertThat(messages(result)).anyMatch(message -> message.contains("artemis.iris.url") && message.contains("ARTEMIS_IRIS_URL"));
    }

    @Test
    void derivesLongEnvironmentNamesFromTheFullConfigurationPath() {
        ResolutionResult result = resolver.resolve(model, Set.of("jenkins"));

        assertThat(value(result, "artemis.continuous-integration.artemis-authentication-token-key"))
                .isEqualTo("${ARTEMIS_CONTINUOUS_INTEGRATION_ARTEMIS_AUTHENTICATION_TOKEN_KEY}");
        assertThat(result.environmentRequirements()).extracting(EnvironmentRequirement::name)
                .contains("ARTEMIS_CONTINUOUS_INTEGRATION_ARTEMIS_AUTHENTICATION_TOKEN_KEY");
    }

    @Test
    void emitsEnvironmentMappingsOnlyWhenTheFeatureIsSelected() {
        ResolutionResult result = resolver.resolve(model, Set.of());

        assertThat(entry(result, "artemis.iris.url")).isEmpty();
        assertThat(entry(result, "artemis.athena.url")).isEmpty();
        assertThat(result.environmentRequirements()).isEmpty();
    }

    @Test
    void producesOneRequirementForEverySelectedEnvironmentMapping() {
        ResolutionResult result = resolver.resolve(model, Set.of("athena"));

        assertThat(result.environmentRequirements()).extracting(EnvironmentRequirement::configKey)
                .containsExactly("artemis.athena.url", "artemis.athena.secret");
    }

    @Test
    void warnsThatLtiNeedsManualRegistration() {
        ResolutionResult result = resolver.resolve(model, Set.of("lti"));

        assertThat(result.messages()).anyMatch(message -> "lti".equals(message.featureId()) && message.message().toLowerCase().contains("registration"));
    }

    @Test
    void notesSelectedFeaturesWithoutMapping() {
        ResolutionResult result = resolver.resolve(model, Set.of("course-workflow"));

        assertThat(result.messages()).anyMatch(message -> GenerationMessage.INFO.equals(message.severity()) && message.message().contains("Course Workflow"));
    }

    @Test
    void ignoresMappingsThatDoNotTargetTheOverlayFile() {
        List<ArtifactMapping> mappings = List.of(
                new ArtifactMapping("application-feature-model.yml", "artemis.demo.enabled", ArtifactMappingSource.SELECTION,
                        JsonNodeFactory.instance.booleanNode(true), JsonNodeFactory.instance.booleanNode(false), null),
                new ArtifactMapping(".env", "DEMO_FLAG", ArtifactMappingSource.SELECTION, JsonNodeFactory.instance.booleanNode(true), null, null),
                new ArtifactMapping(".env", "demo.secret", ArtifactMappingSource.ENVIRONMENT, null, null, true));
        FeatureNode feature = new FeatureNode("demo", "Demo", "feature", true, null, "disabled", null, null, List.of(), List.of(), List.of(), mappings, null);
        FeatureModel syntheticModel = new FeatureModel(new ModelMetadata("test-model", "Test Model", "1.0.0", "draft", null), List.of(feature), List.of(), List.of());

        ResolutionResult result = resolver.resolve(syntheticModel, Set.of("demo"));

        assertThat(result.entries()).extracting(OverlayEntry::path).containsExactly("artemis.demo.enabled");
        assertThat(result.environmentRequirements()).isEmpty();
    }

    public static ArtemisConfigKeyCatalog classpathCatalog() {
        try (InputStream inputStream = ArtifactMappingResolverTest.class.getResourceAsStream("/feature-model/artemis-config-key-catalog.json")) {
            return new ObjectMapper().readValue(inputStream, ArtemisConfigKeyCatalog.class);
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not read the classpath config key catalog.", e);
        }
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
