package de.tum.cit.aet.artemis.featuremodel.extraction.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureConstraint;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConstraintEntry;

/**
 * Derives the pairwise {@code excludes} constraints of an {@code alternative} group: exactly one child of such a group
 * is selected in a deployment, so every pair of emitted children is mutually exclusive. Ids and descriptions follow one
 * fixed template in sibling order, so the assembler, the conformance comparison, and the relation gate agree on them
 * without sharing state.
 */
final class AlternativeGroupConstraints {

    private static final String CONSTRAINT_TYPE_EXCLUDES = "excludes";

    private AlternativeGroupConstraints() {
    }

    /**
     * One emitted child of an alternative group.
     *
     * @param id feature id.
     * @param name feature name used in the constraint description.
     */
    record Child(String id, String name) {
    }

    /**
     * Derives the pairwise exclusions of one alternative group in sibling order.
     *
     * @param groupName name of the group used in the constraint descriptions.
     * @param children emitted children in sibling order.
     * @return derived constraints, one per unordered pair.
     */
    static List<FeatureConstraint> derive(String groupName, List<Child> children) {
        List<FeatureConstraint> constraints = new ArrayList<>();
        for (int source = 0; source < children.size(); source++) {
            for (int target = source + 1; target < children.size(); target++) {
                Child first = children.get(source);
                Child second = children.get(target);
                constraints.add(new FeatureConstraint(first.id() + "-excludes-" + second.id(), CONSTRAINT_TYPE_EXCLUDES, first.id(), second.id(), null,
                        "A deployment selects exactly one option of " + groupName + "; " + first.name() + " and " + second.name() + " are mutually exclusive."));
            }
        }
        return List.copyOf(constraints);
    }

    /**
     * Collects the unordered endpoint pairs of derived constraints, keyed independently of direction.
     *
     * @param derived derived constraints.
     * @return pair keys.
     */
    static Set<String> pairKeys(List<FeatureConstraint> derived) {
        Set<String> keys = new LinkedHashSet<>();
        derived.forEach(constraint -> keys.add(pairKey(constraint.source(), constraint.target())));
        return keys;
    }

    /**
     * Decides whether a declared constraint duplicates a derived exclusion: same type and the same unordered pair.
     *
     * @param entry declared constraint.
     * @param derivedPairKeys pair keys of the derived exclusions.
     * @return true when the declared constraint is redundant.
     */
    static boolean isRedundant(ConstraintEntry entry, Set<String> derivedPairKeys) {
        return CONSTRAINT_TYPE_EXCLUDES.equals(entry.type()) && derivedPairKeys.contains(pairKey(entry.source(), entry.target()));
    }

    /**
     * Builds the direction-independent key of an endpoint pair.
     *
     * @param first one endpoint id.
     * @param second the other endpoint id.
     * @return pair key.
     */
    private static String pairKey(String first, String second) {
        return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
    }
}
