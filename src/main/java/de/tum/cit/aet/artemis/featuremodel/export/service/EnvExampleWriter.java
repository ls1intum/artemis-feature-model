package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.Collection;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

/**
 * Writes a deterministic {@code .env.example} file listing the environment variables referenced by the generated
 * overlay. Variables are sorted, de-duplicated, and emitted with empty values so no secret material is ever included.
 */
@Component
public class EnvExampleWriter {

    /**
     * Writes an {@code .env.example} body from the referenced environment variable names.
     *
     * @param environmentVariables environment variable names referenced by the overlay.
     * @return {@code .env.example} text with one empty assignment per variable, sorted.
     */
    public String write(Collection<String> environmentVariables) {
        TreeSet<String> sorted = new TreeSet<>(environmentVariables);
        StringBuilder builder = new StringBuilder();
        for (String name : sorted) {
            builder.append(name).append("=\n");
        }
        return builder.toString();
    }
}
