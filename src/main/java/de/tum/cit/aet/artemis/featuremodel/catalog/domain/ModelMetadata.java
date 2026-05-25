package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelMetadata(String id, String name, String version, String status, String sourceCommitSha) {

    /**
     * Creates model metadata from older snapshots that do not yet contain lifecycle fields.
     *
     * @param id stable model id.
     * @param name display name.
     * @param version model version.
     */
    public ModelMetadata(String id, String name, String version) {
        this(id, name, version, null, null);
    }
}
