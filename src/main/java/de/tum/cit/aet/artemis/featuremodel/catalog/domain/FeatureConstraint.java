package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import tools.jackson.databind.JsonNode;

public record FeatureConstraint(String id, String type, String source, String target, JsonNode expression, String description) {

    private static final String TYPE_REQUIRES = "requires";

    private static final String TYPE_EXCLUDES = "excludes";

    private static final String TYPE_EXPRESSION = "expression";

    /**
     * Checks whether this constraint is a requires constraint.
     *
     * @return true if this constraint has type {@code requires}.
     */
    @JsonIgnore
    public boolean isRequires() {
        return FeatureConstraint.TYPE_REQUIRES.equals(type);
    }

    /**
     * Checks whether this constraint is an excludes constraint.
     *
     * @return true if this constraint has type {@code excludes}.
     */
    @JsonIgnore
    public boolean isExcludes() {
        return FeatureConstraint.TYPE_EXCLUDES.equals(type);
    }

    /**
     * Checks whether this constraint is an expression constraint.
     *
     * @return true if this constraint has type {@code expression}.
     */
    @JsonIgnore
    public boolean isExpression() {
        return FeatureConstraint.TYPE_EXPRESSION.equals(type);
    }
}
