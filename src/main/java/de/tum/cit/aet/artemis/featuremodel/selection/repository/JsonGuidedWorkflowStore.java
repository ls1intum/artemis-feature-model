package de.tum.cit.aet.artemis.featuremodel.selection.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelBundle;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelBundleLoader;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JsonGuidedWorkflowStore implements GuidedWorkflowStore {

    private static final Logger log = LoggerFactory.getLogger(JsonGuidedWorkflowStore.class);

    private final RuntimeFeatureModelBundle runtimeBundle;

    private volatile GuidedWorkflow activeWorkflow;

    /**
     * Creates a store backed by the already validated complete runtime bundle.
     *
     * @param runtimeBundle validated process-stable runtime bundle.
     */
    @Autowired
    public JsonGuidedWorkflowStore(RuntimeFeatureModelBundle runtimeBundle) {
        this.runtimeBundle = runtimeBundle;
    }

    /**
     * Creates a classpath-only store with no local snapshot source. Convenient for focused unit tests.
     *
     * @param resourceLoader Spring resource loader used to resolve the classpath workflow.
     * @param objectMapper Jackson mapper used to parse the workflow.
     */
    public JsonGuidedWorkflowStore(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this(new RuntimeFeatureModelBundleLoader(SnapshotProperties.classpathFallback(), resourceLoader, objectMapper).load());
    }

    /**
     * Loads and caches the active classpath guided workflow.
     *
     * @return active guided workflow.
     * @throws FeatureModelLoadException if the classpath resource cannot be read or parsed.
     */
    @Override
    public GuidedWorkflow loadActiveWorkflow() {
        GuidedWorkflow cachedWorkflow = activeWorkflow;
        if (cachedWorkflow != null) {
            log.debug("Returning cached active guided workflow from {} bundle.", runtimeBundle.provenance().sourceMode().value());
            return cachedWorkflow;
        }
        synchronized (this) {
            if (activeWorkflow == null) {
                log.debug("Active guided workflow cache is empty, resolving the validated runtime bundle.");
                activeWorkflow = readActiveWorkflow();
            }
            return activeWorkflow;
        }
    }

    /**
     * Reads the active guided workflow from the validated complete runtime bundle.
     *
     * @return parsed guided workflow.
     * @throws FeatureModelLoadException if the resolved resource cannot be read or parsed.
     */
    private GuidedWorkflow readActiveWorkflow() {
        GuidedWorkflow workflow = runtimeBundle.workflow();
        log.info("Loaded active guided workflow '{}' with {} templates, {} steps, and {} review groups from {} bundle.", workflow.workflow().name(),
                workflow.useCaseTemplates().size(), workflow.steps().size(), workflow.finalReviewGroups().size(),
                runtimeBundle.provenance().sourceMode().value());
        return workflow;
    }
}
