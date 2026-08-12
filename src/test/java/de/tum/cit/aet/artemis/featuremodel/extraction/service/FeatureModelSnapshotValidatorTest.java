package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.featuremodel.extraction.artifact.Sha256Digest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactException;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanResult;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SnapshotValidationResult;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.FixtureArtemisSourceRepository;
import tools.jackson.databind.ObjectMapper;

/** Covers pure offline validation, complete payload checksums, corruption, and cross-artifact provenance. */
class FeatureModelSnapshotValidatorTest {

    private static final String ARTEMIS_COMMIT = "aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee";

    private static final String REPOSITORY_COMMIT = "fedcba9876543210fedcba9876543210fedcba98";

    private static final Path FIXTURE_ROOT = Path.of("src/test/resources/extraction");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    private Path outputRoot;

    private Path snapshot;

    @BeforeEach
    void publishValidSnapshot() throws Exception {
        Path inputsRoot = FIXTURE_ROOT.resolve("fixture-inputs");
        FeatureExtractionInputs inputs = new FeatureExtractionInputs(FIXTURE_ROOT.resolve("mini-artemis"),
                FIXTURE_ROOT.resolve("mini-artemis-manifest.yml"), inputsRoot.resolve("guided-workflow.json"),
                inputsRoot.resolve("deployment-profile.json"), outputRoot);
        new ScanStageService(objectMapper).run(inputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, ARTEMIS_COMMIT));
        new ModelStageService(objectMapper).run(inputs);
        new WorkflowStageService(objectMapper).run(inputs);
        new PackageStageService(objectMapper, REPOSITORY_COMMIT).run(inputs);
        snapshot = ExtractionArtifactLayout.forCommit(outputRoot, ARTEMIS_COMMIT).snapshotDirectory();
    }

    @Test
    void validatesCompleteSnapshotWithoutMutatingIt() throws Exception {
        Map<String, byte[]> before = snapshotBytes();

        SnapshotValidationResult result = new FeatureModelSnapshotValidator(objectMapper).validate(snapshot);

        assertThat(result.snapshotId()).startsWith("generated-aaaaaaaa");
        assertThat(result.snapshotDigest()).matches("sha256:[0-9a-f]{64}");
        assertThat(result.payloadCount()).isEqualTo(6);
        assertThat(snapshotBytes()).usingRecursiveComparison().isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = { "config-key-catalog.json", "feature-model.json", "generation-report.json", "guided-workflow.json", "metadata.json",
            "provenance.json" })
    void rejectsCorruptionOfEveryPayload(String fileName) throws Exception {
        Files.writeString(snapshot.resolve(fileName), "corrupt\n");

        assertThatThrownBy(() -> new FeatureModelSnapshotValidator(objectMapper).validate(snapshot)).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("Checksum mismatch").hasMessageContaining(fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = { "checksums.txt", "config-key-catalog.json", "feature-model.json", "generation-report.json", "guided-workflow.json",
            "metadata.json", "provenance.json" })
    void rejectsEveryMissingRequiredFile(String fileName) throws Exception {
        Files.delete(snapshot.resolve(fileName));

        assertThatThrownBy(() -> new FeatureModelSnapshotValidator(objectMapper).validate(snapshot)).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void rejectsExtraPayload() throws Exception {
        Files.writeString(snapshot.resolve("extra.json"), "{}\n");

        assertThatThrownBy(() -> new FeatureModelSnapshotValidator(objectMapper).validate(snapshot)).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("extra=[extra.json]");
    }

    @Test
    void rejectsMetadataCommitMismatchEvenWithUpdatedChecksum() throws Exception {
        Path metadata = snapshot.resolve(SnapshotPublisher.SNAPSHOT_METADATA_FILE);
        Files.writeString(metadata, Files.readString(metadata).replace(ARTEMIS_COMMIT, "bbbbbbbbccccccccddddddddeeeeeeeeffffffff"));
        rewriteChecksums();

        assertThatThrownBy(() -> new FeatureModelSnapshotValidator(objectMapper).validate(snapshot)).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("Artemis commit");
    }

    @Test
    void rejectsProvenanceManifestMismatchEvenWithUpdatedChecksum() throws Exception {
        Path provenance = snapshot.resolve(SnapshotPublisher.SNAPSHOT_PROVENANCE_FILE);
        String original = Files.readString(provenance);
        int digestStart = original.indexOf("sha256:");
        char replacement = original.charAt(digestStart + 7) == '0' ? '1' : '0';
        String changed = original.substring(0, digestStart + 7) + replacement + original.substring(digestStart + 8);
        Files.writeString(provenance, changed);
        rewriteChecksums();

        assertThatThrownBy(() -> new FeatureModelSnapshotValidator(objectMapper).validate(snapshot)).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("manifest digest");
    }

    @Test
    void rejectsNonGeneratedMetadataStatusEvenWithUpdatedChecksum() throws Exception {
        replaceMetadata("\"status\" : \"generated\"", "\"status\" : \"failed\"");

        assertThatThrownBy(() -> new FeatureModelSnapshotValidator(objectMapper).validate(snapshot)).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("status must be generated");
    }

    @Test
    void rejectsForeignExtractorIdentityEvenWithUpdatedChecksum() throws Exception {
        replaceMetadata("feature-model-extractor@" + ScanResult.EXTRACTOR_VERSION, "different-extractor@" + ScanResult.EXTRACTOR_VERSION);

        assertThatThrownBy(() -> new FeatureModelSnapshotValidator(objectMapper).validate(snapshot)).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("extractor identity");
    }

    @Test
    void rejectsInvalidImageIdentityEvenWithUpdatedChecksum() throws Exception {
        replaceMetadata("\"imageDigest\" : \"latest\"", "\"imageDigest\" : \"mutable-main\"");

        assertThatThrownBy(() -> new FeatureModelSnapshotValidator(objectMapper).validate(snapshot)).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("image identity");
    }

    @Test
    void rejectsASchemaV2SnapshotNamingTheUnsupportedSchemaBeforePayloadParsing() throws Exception {
        replaceMetadata("\"schemaVersion\" : 3", "\"schemaVersion\" : 2");
        // A v2 model payload would not even parse against the current mapping contract; the schema gate must fire
        // first, so the model payload is made unparseable to prove no payload parsing happens after the gate.
        Files.writeString(snapshot.resolve(SnapshotPublisher.SNAPSHOT_MODEL_FILE), "not json\n");
        rewriteChecksums();

        assertThatThrownBy(() -> new FeatureModelSnapshotValidator(objectMapper).validate(snapshot)).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("Unsupported snapshot schema version 2").hasMessageContaining("supports schema version 3");
    }

    @Test
    void rejectsAProvenanceWithoutAValidManifestSource() throws Exception {
        Path provenance = snapshot.resolve(SnapshotPublisher.SNAPSHOT_PROVENANCE_FILE);
        Files.writeString(provenance, Files.readString(provenance).replace("\"manifestSource\" : \"repository\"", "\"manifestSource\" : \"unknown\""));
        rewriteChecksums();

        assertThatThrownBy(() -> new FeatureModelSnapshotValidator(objectMapper).validate(snapshot)).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("manifest source");
    }

    private void replaceMetadata(String original, String replacement) throws Exception {
        Path metadata = snapshot.resolve(SnapshotPublisher.SNAPSHOT_METADATA_FILE);
        String content = Files.readString(metadata);
        assertThat(content).contains(original);
        Files.writeString(metadata, content.replace(original, replacement));
        rewriteChecksums();
    }

    private void rewriteChecksums() throws Exception {
        StringBuilder content = new StringBuilder();
        for (String fileName : SnapshotPublisher.PAYLOAD_FILES) {
            content.append(Sha256Digest.of(snapshot.resolve(fileName))).append("  ").append(fileName).append('\n');
        }
        Files.write(snapshot.resolve(SnapshotPublisher.SNAPSHOT_CHECKSUM_FILE), content.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, byte[]> snapshotBytes() throws Exception {
        Map<String, byte[]> bytes = new TreeMap<>();
        try (var paths = Files.list(snapshot)) {
            for (Path file : paths.toList()) {
                bytes.put(file.getFileName().toString(), Files.readAllBytes(file));
            }
        }
        return bytes;
    }
}
