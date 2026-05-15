package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

import tools.jackson.databind.JsonNode;

public record FeatureConstraint(String id, String type, String source, String target, JsonNode expression, String description) {

    private static final String TYPE_REQUIRES = "requires";

    private static final String TYPE_EXCLUDES = "excludes";

    private static final String TYPE_EXPRESSION = "expression";

    public boolean isRequires() {
        return FeatureConstraint.TYPE_REQUIRES.equals(type);
    }

    public boolean isExcludes() {
        return FeatureConstraint.TYPE_EXCLUDES.equals(type);
    }

    public boolean isExpression() {
        return FeatureConstraint.TYPE_EXPRESSION.equals(type);
    }
}
