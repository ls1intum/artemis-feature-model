package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.featuremodel.export.domain.EnvironmentRequirement;

/**
 * Writes a deterministic {@code .env.example} file from the structured environment requirements of a generation run.
 * Artifact-mapping requirements come first, ordered by feature id and variable name, followed by package-only
 * requirements ordered by source and variable name. Every variable is emitted with an empty value and a comment block
 * naming its owner and configuration key, so no secret material is ever included.
 */
@Component
public class EnvExampleWriter {

    /**
     * Writes an {@code .env.example} body from the structured environment requirements.
     *
     * @param environmentRequirements environment requirements of the generation run.
     * @return {@code .env.example} text with one commented empty assignment per requirement.
     */
    public String write(List<EnvironmentRequirement> environmentRequirements) {
        List<String> blocks = new ArrayList<>();
        Set<String> writtenNames = new LinkedHashSet<>();
        for (EnvironmentRequirement requirement : orderedRequirements(environmentRequirements)) {
            if (writtenNames.add(requirement.name())) {
                blocks.add(requirementBlock(requirement));
            }
        }
        return blocks.isEmpty() ? "" : String.join("\n\n", blocks) + "\n";
    }

    /**
     * Renders one requirement as its commented empty assignment block.
     *
     * @param requirement environment requirement.
     * @return block text ending with the empty assignment line, without a trailing newline.
     */
    private String requirementBlock(EnvironmentRequirement requirement) {
        List<String> lines = new ArrayList<>();
        if (requirement.isCatalogKeyed()) {
            lines.add("# " + requirement.featureName());
            lines.add("# Config key: " + requirement.configKey());
        }
        else {
            lines.add("# " + requirement.purpose());
            lines.add("# Provided by: " + requirement.source());
        }
        if (requirement.secret()) {
            lines.add("# SECRET — obtain from the deployment secret store");
        }
        else if (requirement.catalogType() != null) {
            lines.add("# Type: " + requirement.catalogType());
        }
        lines.add(requirement.name() + "=");
        return String.join("\n", lines);
    }

    /**
     * Orders requirements deterministically: artifact-mapping requirements by feature id and variable name, then
     * package-only requirements by source and variable name.
     *
     * @param environmentRequirements environment requirements of the generation run.
     * @return ordered requirements.
     */
    private List<EnvironmentRequirement> orderedRequirements(List<EnvironmentRequirement> environmentRequirements) {
        List<EnvironmentRequirement> mappingRequirements = new ArrayList<>();
        List<EnvironmentRequirement> packageRequirements = new ArrayList<>();
        for (EnvironmentRequirement requirement : environmentRequirements) {
            if (EnvironmentRequirement.SOURCE_ARTIFACT_MAPPING.equals(requirement.source())) {
                mappingRequirements.add(requirement);
            }
            else {
                packageRequirements.add(requirement);
            }
        }
        mappingRequirements.sort(Comparator.comparing(EnvironmentRequirement::featureId).thenComparing(EnvironmentRequirement::name));
        packageRequirements.sort(Comparator.comparing(EnvironmentRequirement::source).thenComparing(EnvironmentRequirement::name));
        List<EnvironmentRequirement> ordered = new ArrayList<>(mappingRequirements);
        ordered.addAll(packageRequirements);
        return ordered;
    }

}
