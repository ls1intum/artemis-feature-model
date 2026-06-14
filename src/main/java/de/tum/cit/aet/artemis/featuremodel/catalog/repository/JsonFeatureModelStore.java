package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JsonFeatureModelStore implements FeatureModelStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFeatureModelStore.class);

    static final String ACTIVE_MODEL_RESOURCE = "classpath:feature-model/functional-feature-model.json";

    private final ResourceLoader resourceLoader;

    private final ObjectMapper objectMapper;

    private final LocalSnapshotRepository snapshotRepository;

    private volatile FeatureModel activeModel;

    /**
     * Creates a JSON-backed feature model store that prefers the active local snapshot and falls back to the classpath
     * resource.
     *
     * @param resourceLoader Spring resource loader used to resolve the classpath model.
     * @param objectMapper Jackson mapper used to parse the model.
     * @param snapshotRepository local snapshot repository used to resolve the active snapshot model file.
     */
    @Autowired
    public JsonFeatureModelStore(ResourceLoader resourceLoader, ObjectMapper objectMapper, LocalSnapshotRepository snapshotRepository) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * Creates a classpath-only store with no local snapshot source. Convenient for focused unit tests.
     *
     * @param resourceLoader Spring resource loader used to resolve the classpath model.
     * @param objectMapper Jackson mapper used to parse the model.
     */
    public JsonFeatureModelStore(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this(resourceLoader, objectMapper, new LocalSnapshotRepository(SnapshotProperties.classpathFallback(), objectMapper));
    }

    /**
     * Loads and caches the active classpath feature model.
     *
     * @return active feature model.
     * @throws FeatureModelLoadException if the classpath resource cannot be read or parsed.
     */
    @Override
    public FeatureModel loadActiveModel() {
        FeatureModel cachedModel = activeModel;
        if (cachedModel != null) {
            log.debug("Returning cached active feature model from {}.", ACTIVE_MODEL_RESOURCE);
            return cachedModel;
        }
        synchronized (this) {
            if (activeModel == null) {
                log.debug("Active feature model cache is empty, loading {}.", ACTIVE_MODEL_RESOURCE);
                activeModel = readActiveModel();
            }
            return activeModel;
        }
    }

    /**
     * Reads the active feature model from the active local snapshot when configured, otherwise from the classpath.
     *
     * @return parsed feature model.
     * @throws FeatureModelLoadException if the resolved resource cannot be read or parsed.
     */
    private FeatureModel readActiveModel() {
        Optional<Resource> snapshotResource = snapshotRepository.activeModelResource();
        if (snapshotResource.isPresent()) {
            return readModelFrom(snapshotResource.get(), "active local snapshot '" + snapshotRepository.activeSnapshotId() + "'");
        }
        Resource resource = resourceLoader.getResource(ACTIVE_MODEL_RESOURCE);
        return readModelFrom(resource, ACTIVE_MODEL_RESOURCE);
    }

    /**
     * Reads and parses a feature model from a resolved resource.
     *
     * @param resource resource to read.
     * @param sourceLabel human-readable source label for logs and errors.
     * @return parsed feature model.
     * @throws FeatureModelLoadException if the resource cannot be read or parsed.
     */
    private FeatureModel readModelFrom(Resource resource, String sourceLabel) {
        try (InputStream inputStream = resource.getInputStream()) {
            FeatureModel model = objectMapper.readValue(inputStream, FeatureModel.class);
            log.info("Loaded active feature model '{}' with {} features, {} relations, and {} constraints from {}.",
                    model.model().name(), model.features().size(), model.relations().size(), model.constraints().size(), sourceLabel);
            return model;
        }
        catch (IOException e) {
            log.error("Could not load active feature model from {}.", sourceLabel, e);
            throw new FeatureModelLoadException("Could not load active feature model from " + sourceLabel + ".", e);
        }
    }
}
