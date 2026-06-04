package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ArtifactMapping(String target, String path, JsonNode valueWhenSelected, JsonNode valueWhenDeselected, String valueFromProfile) {
}
