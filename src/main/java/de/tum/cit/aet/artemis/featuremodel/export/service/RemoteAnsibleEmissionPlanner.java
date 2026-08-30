package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.export.domain.AnsibleBindingCatalog;
import de.tum.cit.aet.artemis.featuremodel.export.domain.RemoteAnsibleEmissionPlan;
import de.tum.cit.aet.artemis.featuremodel.export.domain.RemoteEnvironmentValues;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;

/**
 * Pure emission-plan layer of the remote-ansible deployment package: selection plus binding catalog plus resolved
 * target identity yield a deterministic inventory file plan. The planner performs no IO and holds no Spring state,
 * so every emission semantics is unit-testable in isolation.
 *
 * <p>
 * No environment value enters a planned byte: every admin-owned or secret value is rendered as a
 * {@code lookup('ansible.builtin.env', …)} expression over the user-provisioned environment-variable names, and the
 * target-group section of the hosts file stays empty because a host entry cannot be a lookup — the connection line is
 * owned by the execution environment.
 *
 * <p>
 * The planner is fail-closed. Every selectable feature of the active model must be classified by the catalog (the
 * run-time coverage gate), and a selection state an {@code unsupported} classification cannot express is refused with
 * the catalog's missing-variable reason before any file content is produced.
 */
public class RemoteAnsibleEmissionPlanner {

    /** Package path of the inventory hosts file. */
    static final String HOSTS_FILE = "inventory/hosts";

    private static final String GROUP_VARS_DIR = "inventory/group_vars/";

    /** Wired values group every generated target joins besides the technical and feature groups. */
    private static final String COMMON_CONFIG_GROUP = RemoteEnvironmentValues.RESERVED_GROUP_PREFIX + "common_config";

    private final AnsibleBindingCatalog catalog;

    /**
     * Creates a planner over one binding catalog.
     *
     * @param catalog validated Ansible binding catalog.
     */
    public RemoteAnsibleEmissionPlanner(AnsibleBindingCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Renders the environment lookup expression of a provisioned variable name.
     *
     * @param envVar user-provisioned environment-variable name.
     * @return Jinja environment lookup expression.
     */
    static String envLookup(String envVar) {
        return "{{ lookup('ansible.builtin.env', '" + envVar + "') }}";
    }

    /**
     * Plans the inventory files for a validated selection. Classification runs against the active model regardless of
     * its source mode, so a model ahead of the catalog is refused instead of silently under-configured.
     *
     * @param model active feature model.
     * @param selectedFeatureIds validated selected feature ids.
     * @param environment resolved target identity.
     * @return deterministic emission plan.
     * @throws ArtifactGenerationException if a feature is unclassified, a selection state is unsupported, or a
     *             technical choice is missing.
     */
    public RemoteAnsibleEmissionPlan plan(FeatureModel model, Set<String> selectedFeatureIds, RemoteEnvironmentValues environment) {
        List<RemoteAnsibleEmissionPlan.FeatureClassificationResult> classifications = classify(model, selectedFeatureIds);

        String databaseId = selectedTechnicalChoice(model, selectedFeatureIds, catalog.technical().database().keySet(), "database");
        AnsibleBindingCatalog.FeatureBinding databaseBinding = catalog.technical().database().get(databaseId);
        String ciProviderId = selectedTechnicalChoice(model, selectedFeatureIds, catalog.technical().ciProvider().keySet(), "CI-provider");
        AnsibleBindingCatalog.FeatureBinding ciBinding = catalog.technical().ciProvider().get(ciProviderId);

        String targetGroup = environment.targetGroup();
        String targetMainPath = GROUP_VARS_DIR + targetGroup + "/main.yml";
        String secretsPath = GROUP_VARS_DIR + targetGroup + "/secrets.yml";
        String commonConfigPath = GROUP_VARS_DIR + COMMON_CONFIG_GROUP + ".yml";
        List<AnsibleBindingCatalog.FeatureBinding> boundFeatures = emittedBoundFeatures(model, selectedFeatureIds);

        List<RemoteAnsibleEmissionPlan.PlannedFile> files = new ArrayList<>();
        files.add(new RemoteAnsibleEmissionPlan.PlannedFile(HOSTS_FILE, hostsContent(targetGroup, databaseBinding, ciBinding, boundFeatures)));
        files.add(new RemoteAnsibleEmissionPlan.PlannedFile(targetMainPath, targetMainContent()));
        files.add(new RemoteAnsibleEmissionPlan.PlannedFile(secretsPath, targetSecretsContent()));
        files.add(new RemoteAnsibleEmissionPlan.PlannedFile(commonConfigPath, commonConfigContent()));
        files.add(plannedGroupFile(databaseBinding));
        files.add(plannedGroupFile(ciBinding));
        for (AnsibleBindingCatalog.FeatureBinding boundFeature : boundFeatures) {
            files.add(plannedGroupFile(boundFeature));
        }

        List<RemoteAnsibleEmissionPlan.PlannedEnvReference> envReferences = new ArrayList<>();
        collectEnvironmentEntryReferences(AnsibleBindingCatalog.FILE_TARGET_MAIN, targetMainPath, envReferences);
        for (AnsibleBindingCatalog.SecretEntry secret : catalog.secrets()) {
            envReferences.add(new RemoteAnsibleEmissionPlan.PlannedEnvReference(secret.envVar(), secret.var(), secretsPath,
                    RemoteAnsibleEmissionPlan.ENV_KIND_SECRET));
        }
        collectEnvironmentEntryReferences(AnsibleBindingCatalog.FILE_COMMON_CONFIG, commonConfigPath, envReferences);
        collectBindingEnvReferences(ciBinding, envReferences);
        for (AnsibleBindingCatalog.FeatureBinding boundFeature : boundFeatures) {
            collectBindingEnvReferences(boundFeature, envReferences);
        }

        RemoteAnsibleEmissionPlan.CatalogIdentity catalogIdentity = new RemoteAnsibleEmissionPlan.CatalogIdentity(catalog.catalogVersion(),
                catalog.collectionPin());
        return new RemoteAnsibleEmissionPlan(targetGroup, files, envReferences, classifications, catalogIdentity);
    }

    /**
     * Classifies every selectable feature of the model (by the node's own {@code selectable} flag, so technical and
     * runtime-toggle nodes the extraction pipeline may add are covered) against the catalog and enforces the
     * fail-closed contract: unclassified features and inexpressible selection states abort the run before any content
     * is produced.
     *
     * @param model active feature model.
     * @param selectedFeatureIds selected feature ids.
     * @return classification results in model order.
     * @throws ArtifactGenerationException if a feature is unclassified or a selection state is unsupported.
     */
    private List<RemoteAnsibleEmissionPlan.FeatureClassificationResult> classify(FeatureModel model, Set<String> selectedFeatureIds) {
        Set<String> optionalFeatureIds = optionalFeatureIds(model);
        List<RemoteAnsibleEmissionPlan.FeatureClassificationResult> classifications = new ArrayList<>();
        for (FeatureNode feature : model.features()) {
            if (!feature.selectable()) {
                continue;
            }
            AnsibleBindingCatalog.FeatureBinding binding = catalog.bindingFor(feature.id());
            if (binding == null) {
                throw ArtifactGenerationException.remoteAnsibleUnclassifiedFeature(feature.id(), catalog.catalogVersion(), catalog.collectionPin());
            }
            boolean selected = selectedFeatureIds.contains(feature.id());
            if (AnsibleBindingCatalog.BINDING_UNSUPPORTED.equals(binding.binding())) {
                enforceSupportedState(feature.id(), binding, selected, optionalFeatureIds.contains(feature.id()));
            }
            classifications.add(new RemoteAnsibleEmissionPlan.FeatureClassificationResult(feature.id(), binding.binding(), selected));
        }
        return classifications;
    }

    /**
     * Enforces the fail-closed contract of an unsupported binding: the direction field records which selection state
     * the collection cannot express.
     *
     * @param featureId classified feature id.
     * @param binding unsupported binding.
     * @param selected whether the feature is selected.
     * @param optional whether the feature is optional in the model.
     * @throws ArtifactGenerationException if the selection is in the inexpressible state.
     */
    private void enforceSupportedState(String featureId, AnsibleBindingCatalog.FeatureBinding binding, boolean selected, boolean optional) {
        String direction = binding.unsupportedWhen() == null ? AnsibleBindingCatalog.UNSUPPORTED_WHEN_SELECTED : binding.unsupportedWhen();
        String detail = binding.missingVariable() == null ? binding.reason() : binding.missingVariable();
        boolean inexpressibleWhenSelected = AnsibleBindingCatalog.UNSUPPORTED_WHEN_SELECTED.equals(direction);
        boolean inexpressibleWhenDeselected = AnsibleBindingCatalog.UNSUPPORTED_WHEN_DESELECTED.equals(direction);
        if (!inexpressibleWhenSelected && !inexpressibleWhenDeselected) {
            // An unknown direction must never pass silently: treat every state of the feature as inexpressible.
            throw ArtifactGenerationException.remoteAnsibleUnsupportedFeature(featureId,
                    "The binding catalog declares unknown unsupported direction '" + direction + "'. " + detail);
        }
        if (inexpressibleWhenSelected && selected) {
            throw ArtifactGenerationException.remoteAnsibleUnsupportedFeature(featureId, detail);
        }
        if (inexpressibleWhenDeselected && optional && !selected) {
            throw ArtifactGenerationException.remoteAnsibleUnsupportedFeature(featureId, detail);
        }
    }

    /**
     * Resolves the selected feature id of one technical axis.
     *
     * @param model active feature model.
     * @param selectedFeatureIds selected feature ids.
     * @param axisFeatureIds catalog feature ids of the axis.
     * @param axisLabel axis label for the failure message.
     * @return selected axis feature id.
     * @throws ArtifactGenerationException if the selection contains no choice for the axis.
     */
    private String selectedTechnicalChoice(FeatureModel model, Set<String> selectedFeatureIds, Set<String> axisFeatureIds, String axisLabel) {
        for (FeatureNode feature : model.features()) {
            if (axisFeatureIds.contains(feature.id()) && selectedFeatureIds.contains(feature.id())) {
                return feature.id();
            }
        }
        throw ArtifactGenerationException.remoteAnsibleMissingTechnicalChoice(axisLabel);
    }

    /**
     * Collects the bindings of the bound functional features the selection emits, in model order: presence-gated
     * bindings when their feature is selected, deselection-gated bindings (the collection's module off-switches) when
     * their feature is not.
     *
     * @param model active feature model.
     * @param selectedFeatureIds selected feature ids.
     * @return bound bindings the selection emits.
     */
    private List<AnsibleBindingCatalog.FeatureBinding> emittedBoundFeatures(FeatureModel model, Set<String> selectedFeatureIds) {
        List<AnsibleBindingCatalog.FeatureBinding> boundFeatures = new ArrayList<>();
        for (FeatureNode feature : model.features()) {
            AnsibleBindingCatalog.FeatureBinding binding = catalog.features().get(feature.id());
            if (binding == null || !AnsibleBindingCatalog.BINDING_BOUND.equals(binding.binding())) {
                continue;
            }
            boolean selected = selectedFeatureIds.contains(feature.id());
            if (binding.emittedWhenDeselected() != selected) {
                boundFeatures.add(binding);
            }
        }
        return boundFeatures;
    }

    /**
     * Renders the inventory hosts file: the target group with an empty section — a host entry cannot be an
     * environment lookup, so the connection line is supplied by the execution environment — then one children section
     * per wired group in package order.
     *
     * @param targetGroup inventory group name.
     * @param databaseBinding selected database binding.
     * @param ciBinding selected CI-provider binding.
     * @param boundFeatures bound bindings the selection emits.
     * @return rendered hosts content.
     */
    private String hostsContent(String targetGroup, AnsibleBindingCatalog.FeatureBinding databaseBinding,
            AnsibleBindingCatalog.FeatureBinding ciBinding, List<AnsibleBindingCatalog.FeatureBinding> boundFeatures) {
        List<String> memberships = new ArrayList<>(List.of(RemoteEnvironmentValues.RESERVED_GROUP, COMMON_CONFIG_GROUP, databaseBinding.membership(),
                ciBinding.membership()));
        for (AnsibleBindingCatalog.FeatureBinding boundFeature : boundFeatures) {
            memberships.add(boundFeature.membership());
        }
        StringBuilder content = new StringBuilder();
        content.append('[').append(targetGroup).append("]\n");
        for (String membership : new LinkedHashSet<>(memberships)) {
            content.append('\n').append('[').append(membership).append(":children]\n").append(targetGroup).append('\n');
        }
        return content.toString();
    }

    /**
     * Renders the target-group main values file from the environment entries of the target file; every line embeds
     * its environment lookup expression verbatim.
     *
     * @return rendered file content.
     */
    private String targetMainContent() {
        List<String> lines = new ArrayList<>();
        lines.add("---");
        for (AnsibleBindingCatalog.EnvironmentEntry entry : catalog.environment()) {
            if (AnsibleBindingCatalog.FILE_TARGET_MAIN.equals(entry.file())) {
                lines.addAll(entry.lines());
            }
        }
        return String.join("\n", lines);
    }

    /**
     * Renders the target-group secrets file: every value is an environment lookup expression, never a secret value.
     *
     * @return rendered file content.
     */
    private String targetSecretsContent() {
        List<String> lines = new ArrayList<>();
        lines.add("---");
        lines.add("# Secret values are never stored in this package; every value below is resolved from a");
        lines.add("# control-node environment variable at run time. See README.md for providing the values.");
        for (AnsibleBindingCatalog.SecretEntry secret : catalog.secrets()) {
            lines.add(secret.var() + ": \"" + envLookup(secret.envVar()) + "\"");
        }
        return String.join("\n", lines);
    }

    /**
     * Renders the common configuration values file by merging baseline and environment entries in catalog order,
     * separating groups with blank lines.
     *
     * @return rendered file content.
     */
    private String commonConfigContent() {
        List<CommonConfigEntry> entries = new ArrayList<>();
        for (AnsibleBindingCatalog.BaselineEntry entry : catalog.baseline()) {
            entries.add(new CommonConfigEntry(entry.order(), entry.group(), entry.lines()));
        }
        for (AnsibleBindingCatalog.EnvironmentEntry entry : catalog.environment()) {
            if (AnsibleBindingCatalog.FILE_COMMON_CONFIG.equals(entry.file())) {
                entries.add(new CommonConfigEntry(entry.order(), entry.group(), entry.lines()));
            }
        }
        entries.sort(Comparator.comparingInt(CommonConfigEntry::order));

        List<String> lines = new ArrayList<>();
        lines.add("---");
        Integer currentGroup = null;
        for (CommonConfigEntry entry : entries) {
            if (currentGroup != null && entry.group() != currentGroup) {
                lines.add("");
            }
            currentGroup = entry.group();
            lines.addAll(entry.lines());
        }
        return String.join("\n", lines);
    }

    /**
     * Renders one bound group values file; the catalog lines are emitted verbatim.
     *
     * @param binding bound binding.
     * @return planned group values file.
     */
    private RemoteAnsibleEmissionPlan.PlannedFile plannedGroupFile(AnsibleBindingCatalog.FeatureBinding binding) {
        return new RemoteAnsibleEmissionPlan.PlannedFile(GROUP_VARS_DIR + binding.groupVarsFile(), String.join("\n", binding.lines()));
    }

    /**
     * Collects the identity environment references of the environment entries rendered into one file.
     *
     * @param file catalog target-file marker.
     * @param filePath package-relative path of the rendered file.
     * @param envReferences accumulating reference list.
     */
    private void collectEnvironmentEntryReferences(String file, String filePath, List<RemoteAnsibleEmissionPlan.PlannedEnvReference> envReferences) {
        for (AnsibleBindingCatalog.EnvironmentEntry entry : catalog.environment()) {
            if (file.equals(entry.file())) {
                envReferences.add(new RemoteAnsibleEmissionPlan.PlannedEnvReference(entry.envVar(), entry.var(), filePath,
                        RemoteAnsibleEmissionPlan.ENV_KIND_IDENTITY));
            }
        }
    }

    /**
     * Collects the secret-class environment references of one bound binding.
     *
     * @param binding bound binding.
     * @param envReferences accumulating reference list.
     */
    private void collectBindingEnvReferences(AnsibleBindingCatalog.FeatureBinding binding, List<RemoteAnsibleEmissionPlan.PlannedEnvReference> envReferences) {
        for (AnsibleBindingCatalog.EnvReference reference : binding.envReferences()) {
            envReferences.add(new RemoteAnsibleEmissionPlan.PlannedEnvReference(reference.envVar(), reference.consumer(),
                    GROUP_VARS_DIR + binding.groupVarsFile(), RemoteAnsibleEmissionPlan.ENV_KIND_SECRET));
        }
    }

    /**
     * Derives the optional selectable feature ids of a model: features whose parent relation is optional.
     *
     * @param model active feature model.
     * @return optional feature ids.
     */
    private Set<String> optionalFeatureIds(FeatureModel model) {
        Set<String> optionalIds = new LinkedHashSet<>();
        for (FeatureRelation relation : model.relations()) {
            if (relation.isOptional()) {
                optionalIds.add(relation.childId());
            }
        }
        return optionalIds;
    }

    /**
     * One entry of the merged common configuration file.
     *
     * @param order rendering position.
     * @param group blank-line group id.
     * @param lines rendered lines.
     */
    private record CommonConfigEntry(int order, int group, List<String> lines) {
    }
}
