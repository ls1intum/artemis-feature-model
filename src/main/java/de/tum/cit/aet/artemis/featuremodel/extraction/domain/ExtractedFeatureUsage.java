package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;
import java.util.TreeSet;

/**
 * One {@code @FeatureUsage}-annotated type of the scanned Artemis checkout, persisted under
 * {@code scan/feature-usages.json}. The record keeps the placement as written in source: the class-level label, the
 * method-level overrides, and the {@code @Conditional} and {@code @Profile} guards of the type. Joining the guards to
 * model members is the model stage's concern.
 *
 * @param file checkout-relative path of the annotated Java file.
 * @param line line of the type declaration.
 * @param module first package segment below {@code de.tum.cit.aet.artemis}, or the whole package when it lies outside.
 * @param type simple name of the annotated type.
 * @param classLabel class-level label, or null when only methods are labelled.
 * @param methodLabels method-level labels sorted by method name, label, and line.
 * @param conditionGuards simple names of the condition classes referenced by {@code @Conditional} on the type, sorted.
 * @param profileGuards profile expressions of {@code @Profile} on the type — constant names as written and string
 *            literals verbatim — sorted.
 */
public record ExtractedFeatureUsage(String file, Integer line, String module, String type, String classLabel, List<MethodLabel> methodLabels,
        List<String> conditionGuards, List<String> profileGuards) {

    /** Normalizes every collection to an immutable copy. */
    public ExtractedFeatureUsage {
        methodLabels = methodLabels == null ? List.of() : List.copyOf(methodLabels);
        conditionGuards = conditionGuards == null ? List.of() : List.copyOf(conditionGuards);
        profileGuards = profileGuards == null ? List.of() : List.copyOf(profileGuards);
    }

    /**
     * Returns the labels the type contributes: the class label plus every method label, deduplicated and sorted.
     *
     * @return effective labels.
     */
    public List<String> effectiveLabels() {
        TreeSet<String> labels = new TreeSet<>();
        if (classLabel != null) {
            labels.add(classLabel);
        }
        methodLabels.forEach(methodLabel -> labels.add(methodLabel.label()));
        return List.copyOf(labels);
    }

    /**
     * One method-level {@code @FeatureUsage} placement.
     *
     * @param method method name.
     * @param label declared label.
     * @param line line of the method declaration.
     */
    public record MethodLabel(String method, String label, Integer line) {
    }
}
