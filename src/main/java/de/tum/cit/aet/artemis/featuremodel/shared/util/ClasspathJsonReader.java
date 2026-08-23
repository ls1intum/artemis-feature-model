package de.tum.cit.aet.artemis.featuremodel.shared.util;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads a JSON resource into a typed value, turning every read or parse failure into a controlled
 * {@link FeatureModelLoadException} so loaders fail closed with one consistent exception.
 */
public final class ClasspathJsonReader {

    private ClasspathJsonReader() {
    }

    /**
     * Reads and parses a resource.
     *
     * @param resourceLoader Spring resource loader.
     * @param objectMapper Jackson mapper.
     * @param location resource location, for example {@code classpath:...}.
     * @param type target type.
     * @param label human-readable description of the resource for the failure message.
     * @param <T> target type.
     * @return parsed value.
     * @throws FeatureModelLoadException if the resource cannot be read or parsed.
     */
    public static <T> T read(ResourceLoader resourceLoader, ObjectMapper objectMapper, String location, Class<T> type, String label) {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, type);
        }
        catch (IOException | RuntimeException e) {
            throw new FeatureModelLoadException("Could not load " + label + " from " + location + ".", e);
        }
    }
}
