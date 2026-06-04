package de.tum.cit.aet.artemis.featuremodel.selection.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GuidedWorkflowMetadata(String id, String name, String version, String featureModelId, String featureModelVersion, String defaultTemplateId) {
}
