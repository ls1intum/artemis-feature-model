package de.tum.cit.aet.artemis.featuremodel.selection.repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.LocalSnapshotRepository;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JsonGuidedWorkflowStore implements GuidedWorkflowStore {

    private static final Logger log = LoggerFactory.getLogger(JsonGuidedWorkflowStore.class);

    static final String ACTIVE_WORKFLOW_RESOURCE = "classpath:feature-model/guided-workflow.json";

    private final ResourceLoader resourceLoader;

    private final ObjectMapper objectMapper;

    private final LocalSnapshotRepository snapshotRepository;

    private volatile GuidedWorkflow activeWorkflow;

    /**
     * Creates a JSON-backed guided workflow store that prefers the active local snapshot and falls back to the
     * classpath resource.
     *
     * @param resourceLoader Spring resource loader used to resolve the classpath workflow.
     * @param objectMapper Jackson mapper used to parse the workflow.
     * @param snapshotRepository local snapshot repository used to resolve the active snapshot workflow file.
     */
    @Autowired
    public JsonGuidedWorkflowStore(ResourceLoader resourceLoader, ObjectMapper objectMapper, LocalSnapshotRepository snapshotRepository) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * Creates a classpath-only store with no local snapshot source. Convenient for focused unit tests.
     *
     * @param resourceLoader Spring resource loader used to resolve the classpath workflow.
     * @param objectMapper Jackson mapper used to parse the workflow.
     */
    public JsonGuidedWorkflowStore(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this(resourceLoader, objectMapper, new LocalSnapshotRepository(SnapshotProperties.classpathFallback(), objectMapper));
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
            log.debug("Returning cached active guided workflow from {}.", ACTIVE_WORKFLOW_RESOURCE);
            return cachedWorkflow;
        }
        synchronized (this) {
            if (activeWorkflow == null) {
                log.debug("Active guided workflow cache is empty, loading {}.", ACTIVE_WORKFLOW_RESOURCE);
                activeWorkflow = readActiveWorkflow();
            }
            return activeWorkflow;
        }
    }

    /**
     * Reads the active guided workflow from the active local snapshot when configured, otherwise from the classpath.
     *
     * @return parsed guided workflow.
     * @throws FeatureModelLoadException if the resolved resource cannot be read or parsed.
     */
    private GuidedWorkflow readActiveWorkflow() {
        Optional<Resource> snapshotResource = snapshotRepository.activeWorkflowResource();
        if (snapshotResource.isPresent()) {
            return readWorkflowFrom(snapshotResource.get(), "active local snapshot '" + snapshotRepository.activeSnapshotId() + "'");
        }
        Resource resource = resourceLoader.getResource(ACTIVE_WORKFLOW_RESOURCE);
        return readWorkflowFrom(resource, ACTIVE_WORKFLOW_RESOURCE);
    }

    /**
     * Reads and parses a guided workflow from a resolved resource.
     *
     * @param resource resource to read.
     * @param sourceLabel human-readable source label for logs and errors.
     * @return parsed guided workflow.
     * @throws FeatureModelLoadException if the resource cannot be read or parsed.
     */
    private GuidedWorkflow readWorkflowFrom(Resource resource, String sourceLabel) {
        try (InputStream inputStream = resource.getInputStream()) {
            GuidedWorkflow workflow = objectMapper.readValue(inputStream, GuidedWorkflow.class);
            log.info("Loaded active guided workflow '{}' with {} templates, {} steps, and {} review groups from {}.",
                    workflow.workflow().name(), workflow.useCaseTemplates().size(), workflow.steps().size(), workflow.finalReviewGroups().size(), sourceLabel);
            return workflow;
        }
        catch (IOException e) {
            log.error("Could not load active guided workflow from {}.", sourceLabel, e);
            throw new FeatureModelLoadException("Could not load active guided workflow from " + sourceLabel + ".", e);
        }
    }
}
