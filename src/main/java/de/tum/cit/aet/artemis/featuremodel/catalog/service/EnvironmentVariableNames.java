package de.tum.cit.aet.artemis.featuremodel.catalog.service;

import java.util.Locale;

/**
 * Derives the environment variable name of an environment-sourced configuration path. This is a project-owned
 * convention for the explicit {@code ${VARIABLE}} placeholders the generated overlay contains; it is deliberately not
 * Spring Boot relaxed binding, because Spring never derives these names — the overlay references them literally.
 */
public final class EnvironmentVariableNames {

    private EnvironmentVariableNames() {
    }

    /**
     * Derives the environment variable name of a configuration path: uppercase with the root locale, every maximal
     * run of non-alphanumeric characters replaced by one underscore, and leading/trailing underscores trimmed.
     *
     * @param configPath dotted configuration path of an environment-sourced mapping.
     * @return derived environment variable name.
     * @throws IllegalArgumentException if the derivation yields an empty name.
     */
    public static String derive(String configPath) {
        String name = configPath.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Configuration path '" + configPath + "' derives an empty environment variable name.");
        }
        return name;
    }
}
