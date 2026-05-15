package de.tum.cit.aet.artemis.featuremodel.catalog.dto;

import tools.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureConstraint;

public record ConstraintDTO(String id, String type, String source, String target, JsonNode expression, String description) {

    public static ConstraintDTO fromDomain(FeatureConstraint constraint) {
        return new ConstraintDTO(constraint.id(), constraint.type(), constraint.source(), constraint.target(), constraint.expression(),
                constraint.description());
    }
}
