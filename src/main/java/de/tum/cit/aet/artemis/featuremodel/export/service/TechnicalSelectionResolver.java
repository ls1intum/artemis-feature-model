package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.export.domain.TechnicalSelection;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;
import tools.jackson.databind.JsonNode;

/**
 * Resolves selected non-overlay artifact mappings into explicit technical choices.
 *
 * <p>
 * This resolver is the structural-mapping sibling of {@link ArtifactMappingResolver}. It never writes files and does
 * not assign runtime profile order. Unknown structural targets and malformed values are rejected instead of skipped.
 */
@Component
public class TechnicalSelectionResolver {

    /** Structural mapping target that contributes Spring profile tokens. */
    static final String ENV_TARGET = ".env";

    /** Structural path whose selected value is a comma-separated Spring profile token contribution. */
    static final String SPRING_PROFILES_PATH = "SPRING_PROFILES_ACTIVE";

    /** Structural mapping target that declares the selected database compose file. */
    static final String COMPOSE_TARGET = "docker-compose.override.yml";

    /** Structural path whose selected value names the database compose file. */
    static final String DATABASE_COMPOSE_FILE_PATH = "database.composeFile";

    /**
     * Resolves technical mappings of selected features in feature-model order.
     *
     * @param model active feature model.
     * @param selectedFeatureIds normalized selected feature ids.
     * @return resolved technical selection, empty for models without selected structural mappings.
     * @throws ArtifactGenerationException if a selected non-overlay mapping has an unknown target/path, malformed
     *             value, or conflicts with another selected owner of the same axis.
     */
    public TechnicalSelection resolve(FeatureModel model, Set<String> selectedFeatureIds) {
        Set<String> ciProviderFeatureIds = tokenContributingAlternativeMembers(model);
        ResolutionAccumulator accumulator = new ResolutionAccumulator();

        for (FeatureNode feature : model.features()) {
            if (!selectedFeatureIds.contains(feature.id())) {
                continue;
            }
            resolveFeatureMappings(feature, ciProviderFeatureIds, accumulator);
        }

        return accumulator.toSelection();
    }

    /**
     * Resolves every non-overlay mapping owned by one selected feature.
     *
     * @param feature selected feature.
     * @param ciProviderFeatureIds feature ids belonging to the CI-provider group.
     * @param accumulator mutable resolution state.
     */
    private void resolveFeatureMappings(FeatureNode feature, Set<String> ciProviderFeatureIds, ResolutionAccumulator accumulator) {
        for (ArtifactMapping mapping : feature.artifactMappings()) {
            if (ArtifactMappingResolver.OVERLAY_TARGET.equals(mapping.target())) {
                continue;
            }
            if (isSpringProfileMapping(mapping)) {
                accumulator.addSpringProfiles(feature.id(), selectedTextValue(feature, mapping), ciProviderFeatureIds.contains(feature.id()));
                continue;
            }
            if (isDatabaseMapping(mapping)) {
                accumulator.setDatabase(feature.id(), selectedTextValue(feature, mapping));
                continue;
            }
            throw ArtifactGenerationException.unsupportedTechnicalMapping(feature.id(), mapping.target(), mapping.path());
        }
    }

    /**
     * Returns profile-token contributors that belong to an alternative group.
     *
     * <p>
     * This structural rule identifies CI-provider alternatives without coupling resolution to a particular group id.
     * Database alternatives do not contribute profile tokens, while the mandatory local-VC leaf is not an alternative
     * member.
     *
     * @param model active feature model.
     * @return token-contributing alternative feature ids.
     */
    private Set<String> tokenContributingAlternativeMembers(FeatureModel model) {
        Set<String> alternativeGroupIds = new LinkedHashSet<>();
        for (FeatureRelation relation : model.relations()) {
            if ("group".equals(relation.relationType()) && "alternative".equals(relation.groupType())) {
                alternativeGroupIds.add(relation.childId());
            }
        }

        Set<String> memberIds = new LinkedHashSet<>();
        for (FeatureRelation relation : model.relations()) {
            if (alternativeGroupIds.contains(relation.parentId())) {
                memberIds.add(relation.childId());
            }
        }
        return Set.copyOf(memberIds);
    }

    /**
     * Checks whether a mapping contributes Spring profile tokens.
     *
     * @param mapping mapping to inspect.
     * @return true for the recognized environment target and profile path.
     */
    private boolean isSpringProfileMapping(ArtifactMapping mapping) {
        return ENV_TARGET.equals(mapping.target()) && SPRING_PROFILES_PATH.equals(mapping.path());
    }

    /**
     * Checks whether a mapping selects a database compose file.
     *
     * @param mapping mapping to inspect.
     * @return true for the recognized compose target and database path.
     */
    private boolean isDatabaseMapping(ArtifactMapping mapping) {
        return COMPOSE_TARGET.equals(mapping.target()) && DATABASE_COMPOSE_FILE_PATH.equals(mapping.path());
    }

    /**
     * Reads a required non-blank textual selected value.
     *
     * @param feature mapping owner.
     * @param mapping structural mapping.
     * @return selected text value.
     * @throws ArtifactGenerationException if the value is absent, non-textual, or blank.
     */
    private String selectedTextValue(FeatureNode feature, ArtifactMapping mapping) {
        JsonNode value = mapping.valueWhenSelected();
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw ArtifactGenerationException.invalidTechnicalMappingValue(feature.id(), mapping.target(), mapping.path());
        }
        return value.asString();
    }

    /** Mutable local state kept private so the public result remains immutable. */
    private static final class ResolutionAccumulator {

        private final Set<String> springProfileTokens = new LinkedHashSet<>();

        private String databaseComposeFile;

        private String databaseId;

        private String ciProviderId;

        /**
         * Adds comma-separated profile tokens in declaration order.
         *
         * @param featureId mapping owner.
         * @param value comma-separated tokens.
         * @param ciProvider whether the owner belongs to the CI-provider group.
         */
        private void addSpringProfiles(String featureId, String value, boolean ciProvider) {
            for (String rawToken : value.split(",")) {
                String token = rawToken.trim();
                if (!token.isEmpty()) {
                    springProfileTokens.add(token);
                }
            }
            if (ciProvider) {
                ciProviderId = uniqueOwner("CI provider", ciProviderId, featureId);
            }
        }

        /**
         * Sets the selected database mapping.
         *
         * @param featureId mapping owner.
         * @param composeFile selected compose file.
         */
        private void setDatabase(String featureId, String composeFile) {
            databaseId = uniqueOwner("database", databaseId, featureId);
            if (databaseComposeFile != null && !databaseComposeFile.equals(composeFile)) {
                throw ArtifactGenerationException.conflictingTechnicalSelection("database compose file", databaseComposeFile, composeFile);
            }
            databaseComposeFile = composeFile;
        }

        /**
         * Keeps one owner for a technical axis.
         *
         * @param axis axis label for a controlled error.
         * @param currentOwner current owner, if any.
         * @param nextOwner newly selected owner.
         * @return the stable owner.
         */
        private String uniqueOwner(String axis, String currentOwner, String nextOwner) {
            if (currentOwner != null && !currentOwner.equals(nextOwner)) {
                throw ArtifactGenerationException.conflictingTechnicalSelection(axis, currentOwner, nextOwner);
            }
            return nextOwner;
        }

        /**
         * Creates the immutable resolved selection.
         *
         * @return technical selection.
         */
        private TechnicalSelection toSelection() {
            List<String> profiles = List.copyOf(springProfileTokens);
            Optional<String> composeFile = Optional.ofNullable(databaseComposeFile);
            Optional<String> database = Optional.ofNullable(databaseId);
            Optional<String> ciProvider = Optional.ofNullable(ciProviderId);
            return new TechnicalSelection(profiles, composeFile, database, ciProvider);
        }
    }
}
