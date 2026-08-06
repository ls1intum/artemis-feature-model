package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JsonFeatureModelStore implements FeatureModelStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFeatureModelStore.class);

    private final RuntimeFeatureModelBundle runtimeBundle;

    private volatile FeatureModel activeModel;

    /**
     * Creates a store backed by the already validated complete runtime bundle.
     *
     * @param runtimeBundle validated process-stable runtime bundle.
     */
    @Autowired
    public JsonFeatureModelStore(RuntimeFeatureModelBundle runtimeBundle) {
        this.runtimeBundle = runtimeBundle;
    }

    /**
     * Creates a classpath-only store with no local snapshot source. Convenient for focused unit tests.
     *
     * @param resourceLoader Spring resource loader used to resolve the classpath model.
     * @param objectMapper Jackson mapper used to parse the model.
     */
    public JsonFeatureModelStore(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this(new RuntimeFeatureModelBundleLoader(SnapshotProperties.classpathFallback(), resourceLoader, objectMapper).load());
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
            log.debug("Returning cached active feature model from {} bundle.", runtimeBundle.provenance().sourceMode().value());
            return cachedModel;
        }
        synchronized (this) {
            if (activeModel == null) {
                log.debug("Active feature model cache is empty, resolving the validated runtime bundle.");
                activeModel = readActiveModel();
            }
            return activeModel;
        }
    }

    /**
     * Reads the active feature model from the validated complete runtime bundle.
     *
     * @return parsed feature model.
     * @throws FeatureModelLoadException if the resolved resource cannot be read or parsed.
     */
    private FeatureModel readActiveModel() {
        FeatureModel model = runtimeBundle.model();
        log.info("Loaded active feature model '{}' with {} features, {} relations, and {} constraints from {} bundle.", model.model().name(),
                model.features().size(), model.relations().size(), model.constraints().size(), runtimeBundle.provenance().sourceMode().value());
        return model;
    }
}
