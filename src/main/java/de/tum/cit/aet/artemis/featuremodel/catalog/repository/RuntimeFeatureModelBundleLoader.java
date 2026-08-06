package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactException;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GeneratedSnapshotMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SnapshotProvenance;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SnapshotValidationResult;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.FeatureModelSnapshotValidator;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException;
import tools.jackson.databind.ObjectMapper;

/** Loads the complete runtime bundle from exactly one configured source and validates it before publication. */
public class RuntimeFeatureModelBundleLoader {

    static final String MODEL_RESOURCE = "classpath:feature-model/functional-feature-model.json";

    static final String WORKFLOW_RESOURCE = "classpath:feature-model/guided-workflow.json";

    static final String CATALOG_RESOURCE = "classpath:feature-model/artemis-config-key-catalog.json";

    private static final String IMPORTED_MODELS_DIRECTORY = "imported-models";

    private static final String MODEL_FILE = "feature-model.json";

    private static final String WORKFLOW_FILE = "guided-workflow.json";

    private static final String CATALOG_FILE = "config-key-catalog.json";

    private static final String METADATA_FILE = "metadata.json";

    private static final String PROVENANCE_FILE = "provenance.json";

    private static final String OVERLAY_TARGET = "application-feature-model.yml";

    private static final Pattern SNAPSHOT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*$");

    private final SnapshotProperties properties;

    private final ResourceLoader resourceLoader;

    private final ObjectMapper objectMapper;

    /**
     * Creates a complete-bundle loader.
     *
     * @param properties explicit runtime source configuration.
     * @param resourceLoader loader for classpath fixtures.
     * @param objectMapper mapper for all bundle payloads.
     */
    public RuntimeFeatureModelBundleLoader(SnapshotProperties properties, ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    /**
     * Loads and validates the configured complete runtime bundle.
     *
     * @return immutable runtime bundle.
     * @throws FeatureModelLoadException if any configured source payload is missing, malformed, mixed, or invalid.
     */
    public RuntimeFeatureModelBundle load() {
        if (properties.sourceMode() == FeatureModelSourceMode.SNAPSHOT) {
            return loadSnapshotBundle();
        }
        return loadClasspathBundle();
    }

    private RuntimeFeatureModelBundle loadClasspathBundle() {
        FeatureModel model = readResource(MODEL_RESOURCE, FeatureModel.class);
        GuidedWorkflow workflow = readResource(WORKFLOW_RESOURCE, GuidedWorkflow.class);
        ArtemisConfigKeyCatalog catalog = readResource(CATALOG_RESOURCE, ArtemisConfigKeyCatalog.class);
        validateBundle(model, workflow, catalog);
        RuntimeFeatureModelProvenance provenance = new RuntimeFeatureModelProvenance(FeatureModelSourceMode.CLASSPATH, model.model().id(),
                model.model().version(), null, null, null, null, null, null);
        return new RuntimeFeatureModelBundle(model, workflow, catalog, provenance, null);
    }

    private RuntimeFeatureModelBundle loadSnapshotBundle() {
        String snapshotId = properties.activeSnapshotId();
        if (!SNAPSHOT_ID_PATTERN.matcher(snapshotId).matches()) {
            throw loadFailure("Active feature model snapshot id is invalid.");
        }
        Path root = Path.of(properties.dataRoot(), IMPORTED_MODELS_DIRECTORY).toAbsolutePath().normalize();
        Path directory = root.resolve(snapshotId).normalize();
        if (!directory.startsWith(root)) {
            throw loadFailure("Active feature model snapshot id is invalid.");
        }
        try {
            SnapshotValidationResult validation = new FeatureModelSnapshotValidator(objectMapper).validate(directory);
            if (!snapshotId.equals(validation.snapshotId())) {
                throw loadFailure("Configured active snapshot id does not match the validated snapshot identity.");
            }
            FeatureModel model = readFile(directory, MODEL_FILE, FeatureModel.class);
            GuidedWorkflow workflow = readFile(directory, WORKFLOW_FILE, GuidedWorkflow.class);
            ArtemisConfigKeyCatalog catalog = readFile(directory, CATALOG_FILE, ArtemisConfigKeyCatalog.class);
            GeneratedSnapshotMetadata metadata = readFile(directory, METADATA_FILE, GeneratedSnapshotMetadata.class);
            SnapshotProvenance snapshotProvenance = readFile(directory, PROVENANCE_FILE, SnapshotProvenance.class);
            SnapshotValidationResult finalValidation = new FeatureModelSnapshotValidator(objectMapper).validate(directory);
            if (!validation.equals(finalValidation)) {
                throw loadFailure("Active feature model snapshot changed while it was loading.");
            }
            RuntimeFeatureModelProvenance provenance = new RuntimeFeatureModelProvenance(FeatureModelSourceMode.SNAPSHOT, model.model().id(),
                    model.model().version(), finalValidation.snapshotId(), finalValidation.snapshotDigest(), finalValidation.artemisCommit(),
                    finalValidation.manifestDigest(),
                    snapshotProvenance.featureModelRepositoryCommit(), snapshotProvenance.extractorVersion());
            return new RuntimeFeatureModelBundle(model, workflow, catalog, provenance, metadata);
        }
        catch (IOException | ExtractionArtifactException e) {
            throw new FeatureModelLoadException("Active feature model snapshot '" + snapshotId + "' failed complete validation: " + e.getMessage(), e);
        }
    }

    private <T> T readResource(String location, Class<T> type) {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, type);
        }
        catch (IOException | RuntimeException e) {
            throw new FeatureModelLoadException("Could not load the complete classpath feature-model bundle from " + location + ".", e);
        }
    }

    private <T> T readFile(Path directory, String fileName, Class<T> type) throws IOException {
        try {
            return objectMapper.readValue(java.nio.file.Files.readAllBytes(directory.resolve(fileName)), type);
        }
        catch (RuntimeException e) {
            throw new ExtractionArtifactException("Snapshot payload " + fileName + " is unreadable: " + e.getMessage());
        }
    }

    private void validateBundle(FeatureModel model, GuidedWorkflow workflow, ArtemisConfigKeyCatalog catalog) {
        new FeatureModelIntegrityService().validate(model);
        new GuidedWorkflowIntegrityService().validate(workflow, model);
        Set<String> catalogKeys = new HashSet<>();
        for (ArtemisConfigKeyCatalog.CatalogKey key : catalog.keys()) {
            if (!Set.of(ArtemisConfigKeyCatalog.TYPE_BOOLEAN, ArtemisConfigKeyCatalog.TYPE_STRING, ArtemisConfigKeyCatalog.TYPE_URL).contains(key.type())
                    || !catalogKeys.add(key.key())) {
                throw loadFailure("Runtime config-key catalog contains a duplicate key or unsupported type.");
            }
        }
        for (var feature : model.features()) {
            for (ArtifactMapping mapping : feature.artifactMappings()) {
                if (OVERLAY_TARGET.equals(mapping.target()) && !catalogKeys.contains(mapping.path())) {
                    throw loadFailure("Runtime feature model mapping is absent from its config-key catalog: " + mapping.path() + ".");
                }
            }
        }
    }

    private FeatureModelLoadException loadFailure(String message) {
        return new FeatureModelLoadException(message, null);
    }
}
