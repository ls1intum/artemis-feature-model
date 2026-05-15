package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JsonFeatureModelStore implements FeatureModelStore {

    static final String ACTIVE_MODEL_RESOURCE = "classpath:feature-model/functional-feature-model.json";

    private final ResourceLoader resourceLoader;

    private final ObjectMapper objectMapper;

    private volatile FeatureModel activeModel;

    public JsonFeatureModelStore(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    public FeatureModel loadActiveModel() {
        FeatureModel cachedModel = activeModel;
        if (cachedModel != null) {
            return cachedModel;
        }
        synchronized (this) {
            if (activeModel == null) {
                activeModel = readActiveModel();
            }
            return activeModel;
        }
    }

    private FeatureModel readActiveModel() {
        Resource resource = resourceLoader.getResource(ACTIVE_MODEL_RESOURCE);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, FeatureModel.class);
        }
        catch (IOException e) {
            throw new FeatureModelLoadException("Could not load active feature model from " + ACTIVE_MODEL_RESOURCE + ".", e);
        }
    }
}
