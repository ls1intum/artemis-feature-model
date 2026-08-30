package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelBundleLoader;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.deployment.repository.DeploymentProfileRepository;
import de.tum.cit.aet.artemis.featuremodel.deployment.service.DeploymentProfileService;
import de.tum.cit.aet.artemis.featuremodel.export.domain.DeploymentRepositoryPublishResult;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;
import de.tum.cit.aet.artemis.featuremodel.export.dto.ArtifactGenerationRequest;
import de.tum.cit.aet.artemis.featuremodel.export.dto.RemoteEnvironmentInput;
import de.tum.cit.aet.artemis.featuremodel.validation.service.FeatureModelValidationService;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

/**
 * Publish-orchestration tests against a {@code file://} remote: the published tree, the generate output, and the
 * download ZIP content are byte-identical for the same request, and the commit message derives entirely from the
 * generated package.
 */
class DeploymentPackagePublishServiceTest {

    private static final List<String> FULL_MYSQL_SELECTION = List.of("lecture", "tutorialgroup", "course-workflow", "communication", "exercise-common",
            "programming", "quiz", "text", "modeling", "file-upload", "exam", "plagiarism", "mysql", "integrated-code-lifecycle", "localvc");

    @TempDir
    Path tempDir;

    private DeploymentPackageService deploymentPackageService;

    private DeploymentPackagePublishService publishService;

    private ArtifactPackageService artifactPackageService;

    private String remoteUrl;

    @BeforeEach
    void setUp() throws IOException, GitAPIException {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        ObjectMapper objectMapper = new ObjectMapper();
        FeatureModelTreeService treeService = new FeatureModelTreeService();
        JsonFeatureModelStore store = new JsonFeatureModelStore(resourceLoader, objectMapper);
        FeatureModelCatalogService catalogService = new FeatureModelCatalogService(store, new FeatureModelIntegrityService(), treeService);
        FeatureModelValidationService validationService = new FeatureModelValidationService(catalogService, treeService);
        DeploymentProfileRepository repository = new DeploymentProfileRepository(new SnapshotProperties(tempDir.resolve("data").toString(), null), objectMapper);
        DeploymentProfileService profileService = new DeploymentProfileService(repository);
        ArtifactMappingResolver mappingResolver = new ArtifactMappingResolver(ArtifactMappingResolverTest.classpathCatalog());
        ArtifactGenerationService artifactGenerationService = new ArtifactGenerationService(catalogService, validationService, profileService, mappingResolver,
                new YamlOverlayWriter(), new EnvExampleWriter(), objectMapper);
        AnsibleBindingCatalogLoader catalogLoader = new AnsibleBindingCatalogLoader(resourceLoader, objectMapper);
        deploymentPackageService = new DeploymentPackageService(artifactGenerationService, catalogService, profileService, new TechnicalSelectionResolver(),
                new StaticConfigValidationService(resourceLoader, objectMapper), new RuntimeTemplateWriter(), new RuntimeStackWriter(),
                new RemoteImageStackWriter(), new RuntimeScriptWriter(), new ActiveProfilesDeriver(), new DevIdeTemplateWriter(),
                new RemoteAnsibleValuesWriter(catalogLoader), new EnvExampleWriter(),
                new ArtemisRuntimeSourceResolver(new RuntimeFeatureModelBundleLoader(SnapshotProperties.classpathFallback(), resourceLoader, objectMapper).load(),
                        new ArtemisRuntimeProperties("b1e27eeaaa03e4b41d72cbfe7f503e648dd544a6", "latest")), objectMapper);
        artifactPackageService = new ArtifactPackageService();
        remoteUrl = seedRemoteRepository();
        DeploymentRepositoryPublisher publisher = new DeploymentRepositoryPublisher(
                new DeploymentRepositoryProperties(true, remoteUrl, "deployment", null, null, null, null), objectMapper);
        publishService = new DeploymentPackagePublishService(deploymentPackageService, publisher, catalogLoader);
    }

    @Test
    void publishTreeEqualsGenerateOutputAndDownloadZipContentByteForByte() throws Exception {
        ArtifactGenerationRequest request = remoteRequest(FULL_MYSQL_SELECTION);

        DeploymentRepositoryPublishResult result = publishService.publish(request);
        GeneratedArtifactPackage generated = deploymentPackageService.generate(request);

        Map<String, byte[]> generatedBytes = new LinkedHashMap<>();
        for (GeneratedArtifactFile file : generated.files()) {
            generatedBytes.put(file.path(), file.content().getBytes(StandardCharsets.UTF_8));
        }
        Map<String, byte[]> publishedBytes = publishedTree(result.targetDirectory());
        assertThat(publishedBytes.keySet()).containsExactlyInAnyOrderElementsOf(generatedBytes.keySet());
        for (Map.Entry<String, byte[]> entry : generatedBytes.entrySet()) {
            assertThat(publishedBytes.get(entry.getKey())).as("published bytes of %s", entry.getKey()).isEqualTo(entry.getValue());
        }

        Map<String, byte[]> zipBytes = zippedTree(artifactPackageService.zip(generated, RuntimePackageConstants.REMOTE_ANSIBLE_PACKAGE_ROOT_DIR),
                RuntimePackageConstants.REMOTE_ANSIBLE_PACKAGE_ROOT_DIR);
        assertThat(zipBytes.keySet()).containsExactlyInAnyOrderElementsOf(publishedBytes.keySet());
        for (Map.Entry<String, byte[]> entry : zipBytes.entrySet()) {
            assertThat(publishedBytes.get(entry.getKey())).as("published bytes of %s vs ZIP", entry.getKey()).isEqualTo(entry.getValue());
        }
    }

    @Test
    void commitMessageDerivesEntirelyFromTheGeneratedPackage() throws Exception {
        List<String> reducedWithIris = new ArrayList<>(FULL_MYSQL_SELECTION);
        reducedWithIris.remove("exam");
        reducedWithIris.remove("tutorialgroup");
        reducedWithIris.add("iris");

        publishService.publish(remoteRequest(reducedWithIris));

        String message = headCommitMessage();
        assertThat(message).startsWith("deploy artemis-remote: model ");
        assertThat(message).contains("catalog v2@fce6ad1");
        assertThat(message).contains("\nprofile: ");
        assertThat(message).contains("database: mysql   ci: integrated-code-lifecycle");
        assertThat(message).contains("modules off: atlas, exam, tutorialgroup");
        assertThat(message).contains("integrations: iris");
        assertThat(message).contains("environment: env-channel");
    }

    private ArtifactGenerationRequest remoteRequest(List<String> selection) {
        return new ArtifactGenerationRequest(selection, null, null, "remote-ansible", new RemoteEnvironmentInput("artemis-remote"));
    }

    private String seedRemoteRepository() throws IOException, GitAPIException {
        Path remoteDir = tempDir.resolve("deployment-repo.git");
        try (Git remote = Git.init().setBare(true).setInitialBranch("main").setDirectory(remoteDir.toFile()).call()) {
            assertThat(remote.getRepository().isBare()).isTrue();
        }
        String url = remoteDir.toUri().toString();
        Path seedDir = tempDir.resolve("seed");
        try (Git seed = Git.init().setInitialBranch("main").setDirectory(seedDir.toFile()).call()) {
            Files.writeString(seedDir.resolve("README.md"), "# deployment repository\n");
            seed.add().addFilepattern(".").call();
            PersonIdent seeder = new PersonIdent("seed", "seed@example.invalid");
            seed.commit().setMessage("seed default branch").setAuthor(seeder).setCommitter(seeder).setSign(false).call();
            seed.push().setRemote(url).setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main")).call();
        }
        return url;
    }

    private Map<String, byte[]> publishedTree(String targetDirectory) throws IOException, GitAPIException {
        Path verificationDir = Files.createTempDirectory(tempDir, "verification");
        try (Git verification = Git.cloneRepository().setURI(remoteUrl).setDirectory(verificationDir.toFile()).setBranch("deployment").call()) {
            Path packageDir = verification.getRepository().getWorkTree().toPath().resolve(targetDirectory);
            Map<String, byte[]> bytesByPath = new LinkedHashMap<>();
            try (Stream<Path> paths = Files.walk(packageDir)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    bytesByPath.put(packageDir.relativize(path).toString(), Files.readAllBytes(path));
                }
            }
            return bytesByPath;
        }
    }

    private Map<String, byte[]> zippedTree(byte[] archive, String rootDir) throws IOException {
        Map<String, byte[]> bytesByPath = new LinkedHashMap<>();
        try (ZipInputStream zipStream = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    assertThat(entry.getName()).startsWith(rootDir);
                    bytesByPath.put(entry.getName().substring(rootDir.length()), zipStream.readAllBytes());
                }
            }
        }
        return bytesByPath;
    }

    private String headCommitMessage() throws IOException, GitAPIException {
        Path verificationDir = Files.createTempDirectory(tempDir, "message");
        try (Git verification = Git.cloneRepository().setURI(remoteUrl).setDirectory(verificationDir.toFile()).setBranch("deployment").call();
                RevWalk walk = new RevWalk(verification.getRepository())) {
            RevCommit head = walk.parseCommit(verification.getRepository().resolve(Constants.HEAD));
            return head.getFullMessage();
        }
    }
}
