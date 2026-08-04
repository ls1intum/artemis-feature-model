package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.artifact.Sha256Digest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactException;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GeneratedSnapshotMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanResult;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SnapshotProvenance;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SnapshotValidationResult;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowIntegrityService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Pure, filesystem-read-only validator for the complete deterministic snapshot contract. */
public class FeatureModelSnapshotValidator {

    private static final Pattern CHECKSUM_LINE = Pattern.compile("(sha256:[0-9a-f]{64})  ([a-z0-9-]+\\.[a-z]+)");

    private static final Pattern SHA_256 = Pattern.compile("sha256:[0-9a-f]{64}");

    private static final Pattern COMMIT_SHA = Pattern.compile("[0-9a-f]{40}");

    private final ObjectMapper objectMapper;

    /**
     * Creates the validator.
     *
     * @param objectMapper mapper used to parse snapshot JSON.
     */
    public FeatureModelSnapshotValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Validates an existing snapshot without writing, copying, registering, or activating it.
     *
     * @param directory snapshot directory.
     * @return validated identity summary.
     * @throws IOException if payload bytes cannot be read.
     * @throws ExtractionArtifactException if any contract check fails.
     */
    public SnapshotValidationResult validate(Path directory) throws IOException {
        requireDirectory(directory);
        requireExactFiles(directory);
        Map<String, String> checksums = readChecksums(directory.resolve(SnapshotPublisher.SNAPSHOT_CHECKSUM_FILE));
        verifyChecksums(directory, checksums);

        FeatureModel model = read(directory, SnapshotPublisher.SNAPSHOT_MODEL_FILE, FeatureModel.class);
        GuidedWorkflow workflow = read(directory, SnapshotPublisher.SNAPSHOT_WORKFLOW_FILE, GuidedWorkflow.class);
        ArtemisConfigKeyCatalog catalog = read(directory, SnapshotPublisher.SNAPSHOT_CATALOG_FILE, ArtemisConfigKeyCatalog.class);
        ExtractionReport report = read(directory, SnapshotPublisher.SNAPSHOT_REPORT_FILE, ExtractionReport.class);
        SnapshotProvenance provenance = read(directory, SnapshotPublisher.SNAPSHOT_PROVENANCE_FILE, SnapshotProvenance.class);
        GeneratedSnapshotMetadata metadata = read(directory, SnapshotPublisher.SNAPSHOT_METADATA_FILE, GeneratedSnapshotMetadata.class);

        validateMetadata(metadata, model, provenance);
        validateProvenance(directory, provenance, metadata, report, catalog);
        validateModelWorkflowAndCatalog(model, workflow, catalog);
        return new SnapshotValidationResult(metadata.snapshotId(), Sha256Digest.of(directory.resolve(SnapshotPublisher.SNAPSHOT_CHECKSUM_FILE)),
                provenance.artemisCommit(), provenance.manifestDigest(), checksums.size());
    }

    private void requireDirectory(Path directory) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            fail("Snapshot path is not a readable directory: " + directory + ".");
        }
    }

    private void requireExactFiles(Path directory) throws IOException {
        Set<String> expected = new HashSet<>(SnapshotPublisher.PAYLOAD_FILES);
        expected.add(SnapshotPublisher.SNAPSHOT_CHECKSUM_FILE);
        Set<String> actual = new HashSet<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    fail("Snapshot contains a non-regular entry: " + path.getFileName() + ".");
                }
                actual.add(path.getFileName().toString());
            }
        }
        if (!actual.equals(expected)) {
            Set<String> missing = new HashSet<>(expected);
            missing.removeAll(actual);
            Set<String> extra = new HashSet<>(actual);
            extra.removeAll(expected);
            fail("Snapshot payload set is invalid; missing=" + new java.util.TreeSet<>(missing) + ", extra=" + new java.util.TreeSet<>(extra) + ".");
        }
    }

    private Map<String, String> readChecksums(Path file) throws IOException {
        Map<String, String> checksums = new TreeMap<>();
        for (String line : Files.readAllLines(file)) {
            Matcher matcher = CHECKSUM_LINE.matcher(line);
            if (!matcher.matches()) {
                fail("Invalid checksum line: '" + line + "'.");
            }
            if (checksums.put(matcher.group(2), matcher.group(1)) != null) {
                fail("Duplicate checksum entry for " + matcher.group(2) + ".");
            }
        }
        if (!checksums.keySet().equals(new java.util.TreeSet<>(SnapshotPublisher.PAYLOAD_FILES))) {
            fail("Checksum entries must cover every payload exactly once.");
        }
        return checksums;
    }

    private void verifyChecksums(Path directory, Map<String, String> checksums) throws IOException {
        for (Map.Entry<String, String> entry : checksums.entrySet()) {
            String actual = Sha256Digest.of(directory.resolve(entry.getKey()));
            if (!actual.equals(entry.getValue())) {
                fail("Checksum mismatch for " + entry.getKey() + ".");
            }
        }
    }

    private void validateMetadata(GeneratedSnapshotMetadata metadata, FeatureModel model, SnapshotProvenance provenance) {
        if (metadata.schemaVersion() != GeneratedSnapshotMetadata.CURRENT_SCHEMA_VERSION
                || metadata.snapshotFormatVersion() != SnapshotProvenance.CURRENT_FORMAT_VERSION) {
            fail("Unsupported snapshot metadata or format version.");
        }
        List<String> declaredFiles = List.of(metadata.modelFile(), metadata.workflowFile(), metadata.catalogFile(), metadata.reportFile(),
                metadata.provenanceFile(), metadata.checksumFile());
        List<String> expectedFiles = List.of(SnapshotPublisher.SNAPSHOT_MODEL_FILE, SnapshotPublisher.SNAPSHOT_WORKFLOW_FILE,
                SnapshotPublisher.SNAPSHOT_CATALOG_FILE, SnapshotPublisher.SNAPSHOT_REPORT_FILE, SnapshotPublisher.SNAPSHOT_PROVENANCE_FILE,
                SnapshotPublisher.SNAPSHOT_CHECKSUM_FILE);
        if (!declaredFiles.equals(expectedFiles)) {
            fail("Snapshot metadata declares an unsupported payload name.");
        }
        if (!model.model().id().equals(metadata.modelId()) || !model.model().version().equals(metadata.version())) {
            fail("Snapshot metadata does not match the feature model identity.");
        }
        String expectedId = snapshotId(provenance.artemisCommit(), provenance.manifestDigest());
        if (!expectedId.equals(metadata.snapshotId())) {
            fail("Snapshot id does not match its Artemis commit and manifest digest.");
        }
    }

    private void validateProvenance(Path directory, SnapshotProvenance provenance, GeneratedSnapshotMetadata metadata, ExtractionReport report,
            ArtemisConfigKeyCatalog catalog) throws IOException {
        if (provenance.snapshotFormatVersion() != SnapshotProvenance.CURRENT_FORMAT_VERSION || !COMMIT_SHA.matcher(provenance.artemisCommit()).matches()
                || !COMMIT_SHA.matcher(provenance.featureModelRepositoryCommit()).matches() || !SHA_256.matcher(provenance.manifestDigest()).matches()
                || !SHA_256.matcher(provenance.deploymentProfileDigest()).matches()) {
            fail("Snapshot provenance contains an invalid version, commit, or digest.");
        }
        requireEqual("Artemis commit", provenance.artemisCommit(), metadata.sourceCommit());
        requireEqual("extractor version", provenance.extractorVersion(), ScanResult.EXTRACTOR_VERSION);
        requireEqual("report Artemis commit", report.artemisCommit(), provenance.artemisCommit());
        requireEqual("report manifest digest", report.manifestDigest(), provenance.manifestDigest());
        requireEqual("catalog Artemis commit", catalog.verifiedAgainstArtemisCommit(), provenance.artemisCommit());
        if (!ExtractionReport.STATUS_PASS.equals(report.status())) {
            fail("Generation report is not passing.");
        }
        requireDigest(directory, SnapshotPublisher.SNAPSHOT_MODEL_FILE, provenance.featureModelDigest());
        requireDigest(directory, SnapshotPublisher.SNAPSHOT_WORKFLOW_FILE, provenance.workflowDigest());
        requireDigest(directory, SnapshotPublisher.SNAPSHOT_CATALOG_FILE, provenance.catalogDigest());
        requireDigest(directory, SnapshotPublisher.SNAPSHOT_REPORT_FILE, provenance.generationReportDigest());
    }

    private void validateModelWorkflowAndCatalog(FeatureModel model, GuidedWorkflow workflow, ArtemisConfigKeyCatalog catalog) {
        new FeatureModelIntegrityService().validate(model);
        new GuidedWorkflowIntegrityService().validate(workflow, model);
        Map<String, String> types = new TreeMap<>();
        for (ArtemisConfigKeyCatalog.CatalogKey key : catalog.keys()) {
            if (!Set.of(ArtemisConfigKeyCatalog.TYPE_BOOLEAN, ArtemisConfigKeyCatalog.TYPE_STRING, ArtemisConfigKeyCatalog.TYPE_URL).contains(key.type())
                    || types.put(key.key(), key.type()) != null) {
                fail("Catalog contains a duplicate key or unsupported type for " + key.key() + ".");
            }
        }
        for (var feature : model.features()) {
            for (ArtifactMapping mapping : feature.artifactMappings()) {
                if (!GeneratedModelAssembler.OVERLAY_TARGET.equals(mapping.target())) {
                    continue;
                }
                String type = types.get(mapping.path());
                if (type == null) {
                    fail("Model mapping is absent from the generated catalog: " + mapping.path() + ".");
                }
                validateStaticValue(mapping.path(), type, mapping.valueWhenSelected());
                validateStaticValue(mapping.path(), type, mapping.valueWhenDeselected());
            }
        }
    }

    private void validateStaticValue(String key, String type, JsonNode value) {
        if (value == null || value.isNull()) {
            return;
        }
        boolean compatible = ArtemisConfigKeyCatalog.TYPE_BOOLEAN.equals(type) ? value.isBoolean() : value.isString();
        if (!compatible) {
            fail("Model mapping value is incompatible with catalog type for " + key + ".");
        }
    }

    private <T> T read(Path directory, String fileName, Class<T> type) throws IOException {
        try {
            return objectMapper.readValue(Files.readAllBytes(directory.resolve(fileName)), type);
        }
        catch (RuntimeException e) {
            throw new ExtractionArtifactException("Snapshot payload " + fileName + " is unreadable: " + e.getMessage());
        }
    }

    private void requireDigest(Path directory, String fileName, String expected) throws IOException {
        if (!SHA_256.matcher(expected == null ? "" : expected).matches() || !Sha256Digest.of(directory.resolve(fileName)).equals(expected)) {
            fail("Provenance digest mismatch for " + fileName + ".");
        }
    }

    private void requireEqual(String field, String actual, String expected) {
        if (actual == null || !actual.equals(expected)) {
            fail("Snapshot " + field + " values are inconsistent.");
        }
    }

    private String snapshotId(String artemisCommit, String manifestDigest) {
        return "generated-" + artemisCommit.substring(0, 12) + "-" + manifestDigest.substring("sha256:".length(), "sha256:".length() + 12);
    }

    private void fail(String message) {
        throw new ExtractionArtifactException(message);
    }
}
