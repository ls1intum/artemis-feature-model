package de.tum.cit.aet.artemis.featuremodel.export.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelBundleLoader;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.deployment.repository.DeploymentProfileRepository;
import de.tum.cit.aet.artemis.featuremodel.deployment.service.DeploymentProfileService;
import de.tum.cit.aet.artemis.featuremodel.export.service.ActiveProfilesDeriver;
import de.tum.cit.aet.artemis.featuremodel.export.service.AnsibleBindingCatalogLoader;
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtifactGenerationService;
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtifactMappingResolver;
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtifactMappingResolverTest;
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtifactPackageService;
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtemisRuntimeProperties;
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtemisRuntimeSourceResolver;
import de.tum.cit.aet.artemis.featuremodel.export.service.DevIdeTemplateWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.DeploymentPackagePublishService;
import de.tum.cit.aet.artemis.featuremodel.export.service.DeploymentPackageService;
import de.tum.cit.aet.artemis.featuremodel.export.service.DeploymentRepositoryMarkers;
import de.tum.cit.aet.artemis.featuremodel.export.service.DeploymentRepositoryProperties;
import de.tum.cit.aet.artemis.featuremodel.export.service.DeploymentRepositoryPublisher;
import de.tum.cit.aet.artemis.featuremodel.export.service.EnvExampleWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.RemoteAnsibleValuesWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.RuntimeScriptWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.RuntimeStackWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.RemoteImageStackWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.RuntimeTemplateWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.StaticConfigValidationService;
import de.tum.cit.aet.artemis.featuremodel.export.service.TechnicalSelectionResolver;
import de.tum.cit.aet.artemis.featuremodel.export.service.YamlOverlayWriter;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelExceptionHandler;
import de.tum.cit.aet.artemis.featuremodel.validation.service.FeatureModelValidationService;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

class DeploymentPackageResourceTest {

    private static final String MINIMAL = "[\"course-workflow\",\"communication\",\"exercise-common\",\"programming\",\"quiz\",\"mysql\","
            + "\"integrated-code-lifecycle\",\"localvc\"]";

    private static final String REMOTE_GENERATION_BODY = "{\"selectedFeatureIds\":" + MINIMAL.replace("mysql", "postgresql")
            + ",\"deploymentMode\":\"remote-ansible\",\"remoteEnvironment\":{\"targetName\":\"artemis-remote\"}}";

    private static final String REMOTE_PUBLISH_BODY = publishBody(REMOTE_GENERATION_BODY, null);

    @TempDir
    Path dataRoot;

    private MockMvc mockMvc;

    private MockMvc unconfiguredMockMvc;

    private DeploymentPackageService deploymentPackageService;

    private AnsibleBindingCatalogLoader catalogLoader;

    private ObjectMapper objectMapper;

    private String remoteUrl;

    @BeforeEach
    void setUp() throws IOException, GitAPIException {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        objectMapper = new ObjectMapper();
        FeatureModelTreeService treeService = new FeatureModelTreeService();
        JsonFeatureModelStore store = new JsonFeatureModelStore(resourceLoader, objectMapper);
        FeatureModelCatalogService catalogService = new FeatureModelCatalogService(store, new FeatureModelIntegrityService(), treeService);
        FeatureModelValidationService validationService = new FeatureModelValidationService(catalogService, treeService);
        DeploymentProfileRepository repository = new DeploymentProfileRepository(new SnapshotProperties(dataRoot.toString(), null), objectMapper);
        DeploymentProfileService profileService = new DeploymentProfileService(repository);
        ArtifactMappingResolver mappingResolver = new ArtifactMappingResolver(ArtifactMappingResolverTest.classpathCatalog());
        ArtifactGenerationService artifactGenerationService = new ArtifactGenerationService(catalogService, validationService, profileService, mappingResolver,
                new YamlOverlayWriter(), new EnvExampleWriter(), objectMapper);
        catalogLoader = new AnsibleBindingCatalogLoader(resourceLoader, objectMapper);
        deploymentPackageService = new DeploymentPackageService(artifactGenerationService, catalogService, profileService,
                new TechnicalSelectionResolver(), new StaticConfigValidationService(resourceLoader, objectMapper), new RuntimeTemplateWriter(),
                new RuntimeStackWriter(), new RemoteImageStackWriter(), new RuntimeScriptWriter(), new ActiveProfilesDeriver(), new DevIdeTemplateWriter(),
                new RemoteAnsibleValuesWriter(catalogLoader), new EnvExampleWriter(), new ArtemisRuntimeSourceResolver(
                        new RuntimeFeatureModelBundleLoader(SnapshotProperties.classpathFallback(), resourceLoader, objectMapper).load(),
                        new ArtemisRuntimeProperties("latest")), objectMapper);
        remoteUrl = seededRemoteUrl("deployment-repo.git", true);
        mockMvc = mockMvcFor(new DeploymentRepositoryProperties(true, remoteUrl, List.of(filePrefix()), "deployment", null, null, null, null));
        unconfiguredMockMvc = mockMvcFor(new DeploymentRepositoryProperties(false, null, null, null, null, null, null, null));
    }

    private static String publishBody(String generationBody, String repositoryUrl) {
        String repositoryUrlJson = repositoryUrl == null ? "null" : "\"" + repositoryUrl + "\"";
        return "{\"request\":" + generationBody + ",\"repositoryUrl\":" + repositoryUrlJson + "}";
    }

    private String filePrefix() {
        return dataRoot.toUri().toString();
    }

    private MockMvc mockMvcFor(DeploymentRepositoryProperties repositoryProperties) {
        DeploymentRepositoryPublisher publisher = new DeploymentRepositoryPublisher(repositoryProperties, objectMapper);
        DeploymentPackagePublishService publishService = new DeploymentPackagePublishService(deploymentPackageService, publisher, catalogLoader);
        DeploymentPackageResource resource = new DeploymentPackageResource(deploymentPackageService, new ArtifactPackageService(), publishService);
        return MockMvcBuilders.standaloneSetup(resource).setControllerAdvice(new FeatureModelExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(), new ResourceHttpMessageConverter()).build();
    }

    private String seededRemoteUrl(String repositoryName, boolean withMarker) throws IOException, GitAPIException {
        Path remoteDir = dataRoot.resolve(repositoryName);
        try (Git remote = Git.init().setBare(true).setInitialBranch("main").setDirectory(remoteDir.toFile()).call()) {
            assertThat(remote.getRepository().isBare()).isTrue();
        }
        String seededUrl = remoteDir.toUri().toString();
        Path seedDir = Files.createTempDirectory(dataRoot, "seed");
        try (Git seed = Git.init().setInitialBranch("main").setDirectory(seedDir.toFile()).call()) {
            Files.writeString(seedDir.resolve("README.md"), "# deployment repository\n");
            if (withMarker) {
                DeploymentRepositoryMarkers.writeValidMarker(seedDir);
            }
            seed.add().addFilepattern(".").call();
            PersonIdent seeder = new PersonIdent("seed", "seed@example.invalid");
            seed.commit().setMessage("seed default branch").setAuthor(seeder).setCommitter(seeder).setSign(false).call();
            seed.push().setRemote(seededUrl).setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main")).call();
        }
        return seededUrl;
    }

    @Test
    void previewReturnsConfigurationArtifactsAndRuntimePackageFiles() throws Exception {
        mockMvc.perform(post("/api/feature-model/deployment-package/preview").contentType(MediaType.APPLICATION_JSON).content("{\"selectedFeatureIds\":" + MINIMAL + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.files[*].path", hasItem("config/application-feature-model.yml")))
                .andExpect(jsonPath("$.files[*].path", hasItem("metadata/package-manifest.json")))
                .andExpect(jsonPath("$.files[*].path", hasItem("deployment/local-repo/artemis-feature-model-stack.yml")))
                .andExpect(jsonPath("$.files[*].path", hasItem("deployment/local-repo/docker-compose.override.example.yml")))
                .andExpect(jsonPath("$.files[*].path", hasItem("scripts/start-local-repo.sh"))).andExpect(jsonPath("$.report.mode").value("DEMO"))
                .andExpect(jsonPath("$.downloadAvailable").value(true));
    }

    @Test
    void previewRejectsInvalidSelectionWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/feature-model/deployment-package/preview").contentType(MediaType.APPLICATION_JSON).content("{\"selectedFeatureIds\":[\"iris\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ARTIFACT_GENERATION_INVALID_SELECTION"));
    }

    @Test
    void previewReturnsNotFoundForUnknownProfile() throws Exception {
        mockMvc.perform(post("/api/feature-model/deployment-package/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedFeatureIds\":" + MINIMAL + ",\"profileId\":\"missing-profile\"}")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPLOYMENT_PROFILE_NOT_FOUND"));
    }

    @Test
    void previewRejectsAnUnknownDeploymentModeWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/feature-model/deployment-package/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedFeatureIds\":" + MINIMAL + ",\"deploymentMode\":\"cloud-magic\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARTIFACT_GENERATION_UNKNOWN_DEPLOYMENT_MODE"));
    }

    @Test
    void previewRejectsADeploymentModeTheProfileDoesNotSupportWithBadRequest() throws Exception {
        Path profileDirectory = dataRoot.resolve("deployment-profiles");
        Files.createDirectories(profileDirectory);
        Files.writeString(profileDirectory.resolve("docker-only-profile.json"),
                "{\"id\":\"docker-only-profile\",\"name\":\"Docker Only\",\"version\":\"1.0.0\",\"status\":\"published\",\"supportedDeploymentModes\":[\"local-docker\"]}");

        mockMvc.perform(post("/api/feature-model/deployment-package/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedFeatureIds\":" + MINIMAL + ",\"profileId\":\"docker-only-profile\",\"deploymentMode\":\"dev-ide\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ARTIFACT_GENERATION_UNSUPPORTED_DEPLOYMENT_MODE"));
    }

    @Test
    void downloadReturnsZipAttachment() throws Exception {
        mockMvc.perform(post("/api/feature-model/deployment-package/download").contentType(MediaType.APPLICATION_JSON).content("{\"selectedFeatureIds\":" + MINIMAL + "}"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("application/zip")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("artemis-feature-model-deployment-package.zip")));
    }

    @Test
    void downloadZipContainsExpectedRootDirectoryAndFiles() throws Exception {
        byte[] archive = mockMvc
                .perform(post("/api/feature-model/deployment-package/download").contentType(MediaType.APPLICATION_JSON).content("{\"selectedFeatureIds\":" + MINIMAL + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        List<String> names = entryNames(archive);
        assertThat(names).contains("artemis-feature-model-deployment-package/README.md", "artemis-feature-model-deployment-package/metadata/package-manifest.json",
                "artemis-feature-model-deployment-package/deployment/local-repo/docker-compose.override.example.yml",
                "artemis-feature-model-deployment-package/scripts/start-local-repo.sh");
        assertThat(names).allMatch(name -> name.startsWith("artemis-feature-model-deployment-package/"));
    }

    @Test
    void downloadsTheDevIdePackageWithTheRunConfigurationAndWithoutRuntimeScripts() throws Exception {
        byte[] archive = mockMvc
                .perform(post("/api/feature-model/deployment-package/download").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedFeatureIds\":" + MINIMAL + ",\"deploymentMode\":\"dev-ide\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        List<String> names = entryNames(archive);
        assertThat(names).contains("artemis-feature-model-deployment-package/intellij/runConfigurations/Artemis_Server__Feature_Model_Selection_.xml",
                "artemis-feature-model-deployment-package/config/application-feature-model.yml",
                "artemis-feature-model-deployment-package/metadata/static-config-validation.json");
        assertThat(names).noneMatch(name -> name.contains("scripts/")).noneMatch(name -> name.contains("deployment/local-repo/"));
    }

    @Test
    void publishTargetReportsTheConfiguredDestinationWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/feature-model/deployment-package/publish-target")).andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true)).andExpect(jsonPath("$.defaultRepositoryUrl").value(remoteUrl))
                .andExpect(jsonPath("$.branch").value("deployment")).andExpect(jsonPath("$.targetDirectoryRoot").value("deployments"))
                .andExpect(jsonPath("$.repositoryUrl").doesNotExist());
    }

    @Test
    void publishTargetReportsAnUnconfiguredInstance() throws Exception {
        unconfiguredMockMvc.perform(get("/api/feature-model/deployment-package/publish-target")).andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false)).andExpect(jsonPath("$.defaultRepositoryUrl").doesNotExist());
    }

    @Test
    void publishTargetIsConfiguredWithoutADefaultUrl() throws Exception {
        MockMvc withoutDefault = mockMvcFor(new DeploymentRepositoryProperties(true, null, List.of(filePrefix()), "deployment", null, null, null, null));

        withoutDefault.perform(get("/api/feature-model/deployment-package/publish-target")).andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true)).andExpect(jsonPath("$.defaultRepositoryUrl").doesNotExist());
    }

    @Test
    void publishCreatesACommitAndReportsIt() throws Exception {
        mockMvc.perform(post("/api/feature-model/deployment-package/publish").contentType(MediaType.APPLICATION_JSON).content(REMOTE_PUBLISH_BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.repositoryUrl").value(remoteUrl)).andExpect(jsonPath("$.branch").value("deployment"))
                .andExpect(jsonPath("$.targetDirectory").value("deployments/artemis-remote/package"))
                .andExpect(jsonPath("$.commitSha").isNotEmpty()).andExpect(jsonPath("$.upToDate").value(false));

        mockMvc.perform(post("/api/feature-model/deployment-package/publish").contentType(MediaType.APPLICATION_JSON).content(REMOTE_PUBLISH_BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.upToDate").value(true));
    }

    @Test
    void publishRoutesToTheRequestedRepositoryAndReportsIt() throws Exception {
        String requestedUrl = seededRemoteUrl("requested-repo.git", true);

        mockMvc.perform(post("/api/feature-model/deployment-package/publish").contentType(MediaType.APPLICATION_JSON)
                .content(publishBody(REMOTE_GENERATION_BODY.replace("artemis-remote", "chair-vm"), requestedUrl))).andExpect(status().isOk())
                .andExpect(jsonPath("$.repositoryUrl").value(requestedUrl)).andExpect(jsonPath("$.targetDirectory").value("deployments/chair-vm/package"))
                .andExpect(jsonPath("$.upToDate").value(false));

        try (Git defaultRemote = Git.open(dataRoot.resolve("deployment-repo.git").toFile());
                Git requestedRemote = Git.open(dataRoot.resolve("requested-repo.git").toFile())) {
            assertThat(defaultRemote.getRepository().resolve("refs/heads/deployment")).isNull();
            assertThat(requestedRemote.getRepository().resolve("refs/heads/deployment")).isNotNull();
        }
    }

    @Test
    void publishWithoutAnyRepositoryUrlIsABadRequest() throws Exception {
        MockMvc withoutDefault = mockMvcFor(new DeploymentRepositoryProperties(true, null, List.of(filePrefix()), "deployment", null, null, null, null));

        withoutDefault.perform(post("/api/feature-model/deployment-package/publish").contentType(MediaType.APPLICATION_JSON).content(REMOTE_PUBLISH_BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PUBLISH_REQUIRES_REPOSITORY_URL"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void publishToADisallowedRepositoryUrlIsABadRequestListingThePrefixes() throws Exception {
        mockMvc.perform(post("/api/feature-model/deployment-package/publish").contentType(MediaType.APPLICATION_JSON)
                .content(publishBody(REMOTE_GENERATION_BODY, "http://github.com/owner/repository.git"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PUBLISH_REPOSITORY_NOT_ALLOWED")).andExpect(jsonPath("$.message", containsString(filePrefix())));
    }

    @Test
    void publishWithAnInexactTargetNameIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/feature-model/deployment-package/publish").contentType(MediaType.APPLICATION_JSON)
                .content(publishBody(REMOTE_GENERATION_BODY.replace("artemis-remote", "Chair VM"), null))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PUBLISH_INVALID_TARGET_NAME")).andExpect(jsonPath("$.message", containsString("'Chair VM'")));
    }

    @Test
    void publishToARepositoryWithoutTheMarkerIsAConflictAndPushesNothing() throws Exception {
        String unmarkedUrl = seededRemoteUrl("unmarked-repo.git", false);

        mockMvc.perform(post("/api/feature-model/deployment-package/publish").contentType(MediaType.APPLICATION_JSON)
                .content(publishBody(REMOTE_GENERATION_BODY, unmarkedUrl))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PUBLISH_NOT_A_DEPLOYMENT_REPOSITORY"))
                .andExpect(jsonPath("$.message", containsString("deployment-repository.json")));

        try (Git unmarkedRemote = Git.open(dataRoot.resolve("unmarked-repo.git").toFile())) {
            assertThat(unmarkedRemote.getRepository().resolve("refs/heads/deployment")).isNull();
        }
    }

    @Test
    void publishWithoutAGenerationRequestIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/feature-model/deployment-package/publish").contentType(MediaType.APPLICATION_JSON).content("{\"repositoryUrl\":null}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PUBLISH_WRONG_DEPLOYMENT_MODE"));
    }

    @Test
    void mysqlRemoteDownloadAndPublishAreRefusedWithoutChangingTheRemote() throws Exception {
        String mysqlGenerationBody = REMOTE_GENERATION_BODY.replace("postgresql", "mysql");
        Path remoteDirectory = dataRoot.resolve("deployment-repo.git");
        try (Git remote = Git.open(remoteDirectory.toFile())) {
            var originalHead = remote.getRepository().resolve("refs/heads/main");
            for (String action : List.of("download", "publish")) {
                String body = "publish".equals(action) ? publishBody(mysqlGenerationBody, null) : mysqlGenerationBody;
                mockMvc.perform(post("/api/feature-model/deployment-package/" + action)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("ARTIFACT_GENERATION_REMOTE_ANSIBLE_UNSUPPORTED_FEATURE"))
                        .andExpect(jsonPath("$.featureId").value("mysql"))
                        .andExpect(jsonPath("$.reason", containsString("removed MySQL support")))
                        .andExpect(jsonPath("$.message", containsString("mysql")))
                        .andExpect(jsonPath("$.message", containsString("removed MySQL support")));
            }
            assertThat(remote.getRepository().resolve("refs/heads/main")).isEqualTo(originalHead);
            assertThat(remote.getRepository().resolve("refs/heads/deployment")).isNull();
            assertThat(remote.log().add(originalHead).call()).hasSize(1);
        }
    }

    @Test
    void publishRejectsANonRemoteDeploymentModeWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/feature-model/deployment-package/publish").contentType(MediaType.APPLICATION_JSON).content(publishBody(
                "{\"selectedFeatureIds\":" + MINIMAL + ",\"deploymentMode\":\"local-docker\",\"remoteEnvironment\":{\"targetName\":\"artemis-remote\"}}", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PUBLISH_WRONG_DEPLOYMENT_MODE"));

        mockMvc.perform(post("/api/feature-model/deployment-package/publish").contentType(MediaType.APPLICATION_JSON)
                .content(publishBody("{\"selectedFeatureIds\":" + MINIMAL + "}", null))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PUBLISH_WRONG_DEPLOYMENT_MODE"));
    }

    @Test
    void publishRejectsAMissingTargetNameWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/feature-model/deployment-package/publish").contentType(MediaType.APPLICATION_JSON)
                .content(publishBody("{\"selectedFeatureIds\":" + MINIMAL + ",\"deploymentMode\":\"remote-ansible\"}", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PUBLISH_REQUIRES_TARGET_NAME"));

        mockMvc.perform(post("/api/feature-model/deployment-package/publish").contentType(MediaType.APPLICATION_JSON).content(publishBody(
                "{\"selectedFeatureIds\":" + MINIMAL + ",\"deploymentMode\":\"remote-ansible\",\"remoteEnvironment\":{\"targetName\":\"  \"}}", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PUBLISH_REQUIRES_TARGET_NAME"));
    }

    @Test
    void publishOnAnUnconfiguredInstanceIsRefused() throws Exception {
        unconfiguredMockMvc.perform(post("/api/feature-model/deployment-package/publish").contentType(MediaType.APPLICATION_JSON).content(REMOTE_PUBLISH_BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PUBLISH_NOT_CONFIGURED"));
    }

    private List<String> entryNames(byte[] archive) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zipStream = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}
