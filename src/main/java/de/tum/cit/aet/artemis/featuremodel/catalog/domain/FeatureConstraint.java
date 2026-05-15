package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

import tools.jackson.databind.JsonNode;

public record FeatureConstraint(String id, String type, String source, String target, JsonNode expression, String description) {

    public boolean isRequires() {
        return "requires".equals(type);
    }

    public boolean isExcludes() {
        return "excludes".equals(type);
    }

    public boolean isExpression() {
        return "expression".equals(type);
    }
}
