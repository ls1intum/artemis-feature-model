package de.tum.cit.aet.artemis.featuremodel.selection.repository;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JsonGuidedWorkflowStore implements GuidedWorkflowStore {

    private static final Logger log = LoggerFactory.getLogger(JsonGuidedWorkflowStore.class);

    static final String ACTIVE_WORKFLOW_RESOURCE = "classpath:feature-model/guided-workflow.json";

    private final ResourceLoader resourceLoader;

    private final ObjectMapper objectMapper;

    private volatile GuidedWorkflow activeWorkflow;

    /**
     * Creates a JSON-backed guided workflow store.
     *
     * @param resourceLoader Spring resource loader used to resolve the classpath workflow.
     * @param objectMapper Jackson mapper used to parse the workflow.
     */
    public JsonGuidedWorkflowStore(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
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
     * Reads the active guided workflow resource from the classpath.
     *
     * @return parsed guided workflow.
     * @throws FeatureModelLoadException if the classpath resource cannot be read or parsed.
     */
    private GuidedWorkflow readActiveWorkflow() {
        Resource resource = resourceLoader.getResource(ACTIVE_WORKFLOW_RESOURCE);
        try (InputStream inputStream = resource.getInputStream()) {
            GuidedWorkflow workflow = objectMapper.readValue(inputStream, GuidedWorkflow.class);
            log.info("Loaded active guided workflow '{}' with {} templates, {} steps, and {} review groups from {}.",
                    workflow.workflow().name(), workflow.useCaseTemplates().size(), workflow.steps().size(), workflow.finalReviewGroups().size(),
                    ACTIVE_WORKFLOW_RESOURCE);
            return workflow;
        }
        catch (IOException e) {
            log.error("Could not load active guided workflow from {}.", ACTIVE_WORKFLOW_RESOURCE, e);
            throw new FeatureModelLoadException("Could not load active guided workflow from " + ACTIVE_WORKFLOW_RESOURCE + ".", e);
        }
    }
}
