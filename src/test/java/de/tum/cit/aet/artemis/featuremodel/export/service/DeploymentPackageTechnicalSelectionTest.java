package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.TestFeatureModels;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentModes;
import de.tum.cit.aet.artemis.featuremodel.deployment.repository.DeploymentProfileRepository;
import de.tum.cit.aet.artemis.featuremodel.deployment.service.DeploymentProfileService;
import de.tum.cit.aet.artemis.featuremodel.export.domain.DeploymentPackageManifest;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;
import de.tum.cit.aet.artemis.featuremodel.export.domain.TechnicalSelectionMetadata;
import de.tum.cit.aet.artemis.featuremodel.export.dto.ArtifactGenerationRequest;
import de.tum.cit.aet.artemis.featuremodel.validation.service.FeatureModelValidationService;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

class DeploymentPackageTechnicalSelectionTest {

    private static final List<String> TECHNICAL_SELECTION = List.of("exercise-common", "programming", "quiz", "postgresql",
            "integrated-code-lifecycle", "localvc");

    @TempDir
    Path dataRoot;

    private ObjectMapper objectMapper;

    private DeploymentPackageService service;

    @BeforeEach
    void setUp() {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        objectMapper = new ObjectMapper();
        FeatureModel model = technicalModel();
        FeatureModelTreeService treeService = new FeatureModelTreeService();
        FeatureModelCatalogService catalogService = new FeatureModelCatalogService(() -> model, new FeatureModelIntegrityService(), treeService);
        FeatureModelValidationService validationService = new FeatureModelValidationService(catalogService, treeService);
        DeploymentProfileRepository repository = new DeploymentProfileRepository(new SnapshotProperties(dataRoot.toString(), null), objectMapper);
        DeploymentProfileService profileService = new DeploymentProfileService(repository);
        ArtifactGenerationService artifactService = new ArtifactGenerationService(catalogService, validationService, profileService,
                new ArtifactMappingResolver(new ProfileParameterResolver()), new YamlOverlayWriter(), new EnvExampleWriter(), objectMapper);
        service = new DeploymentPackageService(artifactService, catalogService, profileService, new TechnicalSelectionResolver(),
                new StaticConfigValidationService(resourceLoader, objectMapper), new RuntimeTemplateWriter(), new RuntimeScriptWriter(),
                new ActiveProfilesDeriver(), new DevIdeTemplateWriter(), objectMapper);
    }

    @Test
    void recordsTechnicalSelectionWithoutChangingLocalDockerBehaviorFields() {
        GeneratedArtifactPackage result = service.generate(new ArtifactGenerationRequest(TECHNICAL_SELECTION, null, null));

        assertTechnicalReport(result);
        DeploymentPackageManifest manifest = manifest(result);
        assertTechnicalMetadata(manifest.technicalSelection());
        assertThat(manifest.database().type()).isEqualTo("mysql");
        assertThat(manifest.database().mode()).isEqualTo("local-container");
    }

    @Test
    void recordsTheSameUnconsumedSelectionInTheDevIdeManifest() {
        ArtifactGenerationRequest request = new ArtifactGenerationRequest(TECHNICAL_SELECTION, null, null, DeploymentModes.DEV_IDE);

        GeneratedArtifactPackage result = service.generate(request);

        assertTechnicalReport(result);
        DeploymentPackageManifest manifest = manifest(result);
        assertTechnicalMetadata(manifest.technicalSelection());
        assertThat(manifest.database()).isNull();
    }

    private void assertTechnicalReport(GeneratedArtifactPackage result) {
        assertTechnicalMetadata(result.report().technicalSelection());
        String reportJson = content(file(result, ArtifactGenerationService.REPORT_FILE));
        assertThat(reportJson).contains("\"technicalSelection\"", "\"recorded-not-consumed-stage-1\"");
    }

    private void assertTechnicalMetadata(TechnicalSelectionMetadata metadata) {
        assertThat(metadata.databaseId()).isEqualTo("postgresql");
        assertThat(metadata.databaseComposeFile()).isEqualTo("docker/postgres.yml");
        assertThat(metadata.databaseDisposition()).isEqualTo(TechnicalSelectionMetadata.DISPOSITION_RECORDED_NOT_CONSUMED);
        assertThat(metadata.ciProviderId()).isEqualTo("integrated-code-lifecycle");
        assertThat(metadata.springProfileTokens()).containsExactly("localci", "buildagent", "localvc");
        assertThat(metadata.ciProviderDisposition()).isEqualTo(TechnicalSelectionMetadata.DISPOSITION_RECORDED_NOT_CONSUMED);
    }

    private DeploymentPackageManifest manifest(GeneratedArtifactPackage result) {
        return objectMapper.readValue(content(file(result, DeploymentPackageService.MANIFEST_FILE)), DeploymentPackageManifest.class);
    }

    private GeneratedArtifactFile file(GeneratedArtifactPackage result, String path) {
        for (GeneratedArtifactFile file : result.files()) {
            if (path.equals(file.path())) {
                return file;
            }
        }
        throw new IllegalArgumentException("Missing generated file " + path);
    }

    private String content(GeneratedArtifactFile file) {
        return file.content();
    }

    private FeatureModel technicalModel() {
        FeatureModel base = TestFeatureModels.baseModel();
        List<FeatureNode> features = new ArrayList<>(base.features());
        features.add(group("database"));
        features.add(technicalFeature("mysql", TechnicalSelectionResolver.COMPOSE_TARGET,
                TechnicalSelectionResolver.DATABASE_COMPOSE_FILE_PATH, "docker/mysql.yml"));
        features.add(technicalFeature("postgresql", TechnicalSelectionResolver.COMPOSE_TARGET,
                TechnicalSelectionResolver.DATABASE_COMPOSE_FILE_PATH, "docker/postgres.yml"));
        features.add(group("ci-provider"));
        features.add(technicalFeature("integrated-code-lifecycle", TechnicalSelectionResolver.ENV_TARGET,
                TechnicalSelectionResolver.SPRING_PROFILES_PATH, "localci,buildagent"));
        features.add(technicalFeature("jenkins", TechnicalSelectionResolver.ENV_TARGET,
                TechnicalSelectionResolver.SPRING_PROFILES_PATH, "jenkins"));
        features.add(technicalFeature("localvc", TechnicalSelectionResolver.ENV_TARGET,
                TechnicalSelectionResolver.SPRING_PROFILES_PATH, "localvc"));

        List<FeatureRelation> relations = new ArrayList<>(base.relations());
        relations.add(new FeatureRelation("artemis", "database", "group", "alternative", 2));
        relations.add(new FeatureRelation("database", "mysql", "optional", null, 1));
        relations.add(new FeatureRelation("database", "postgresql", "optional", null, 2));
        relations.add(new FeatureRelation("artemis", "ci-provider", "group", "alternative", 3));
        relations.add(new FeatureRelation("ci-provider", "integrated-code-lifecycle", "optional", null, 1));
        relations.add(new FeatureRelation("ci-provider", "jenkins", "optional", null, 2));
        relations.add(new FeatureRelation("artemis", "localvc", "mandatory", null, 4));
        return new FeatureModel(base.model(), features, relations, base.constraints());
    }

    private FeatureNode group(String id) {
        return new FeatureNode(id, id, "group", false, null, "not_applicable", null, "technical", List.of("maintainer"), List.of(),
                List.of(), List.of(), null);
    }

    private FeatureNode technicalFeature(String id, String target, String path, String value) {
        ArtifactMapping mapping = new ArtifactMapping(target, path, objectMapper.valueToTree(value), null, null, false, false);
        return new FeatureNode(id, id, "feature", true, null, "disabled", null, "technical", List.of("maintainer"), List.of("maintainer"),
                List.of(), List.of(mapping), null);
    }
}
