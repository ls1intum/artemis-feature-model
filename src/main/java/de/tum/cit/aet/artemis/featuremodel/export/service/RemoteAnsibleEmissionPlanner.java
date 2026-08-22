package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * environment values yield a deterministic inventory file plan. The planner performs no IO and holds no Spring state,
 * so every emission semantics is unit-testable in isolation.
 *
 * <p>
 * The planner is fail-closed. Every selectable feature of the active model must be classified by the catalog (the
 * run-time coverage gate), and a selection state an {@code unsupported} classification cannot express is refused with
 * the catalog's missing-variable reason before any file content is produced.
 */
public class RemoteAnsibleEmissionPlanner {

    /** Package path of the inventory hosts file. */
    public static final String HOSTS_FILE = "inventory/hosts";

    private static final String GROUP_VARS_DIR = "inventory/group_vars/";

    private static final Set<String> SELECTABLE_KINDS = Set.of("module", "feature");

    private static final String RELATION_TYPE_OPTIONAL = "optional";

    private static final String VALUE_TOKEN = "{value}";

    private static final String VAULT_SERVER_NAME_TOKEN = "{vaultServerName}";

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
     * Plans the inventory files for a validated selection. Classification runs against the active model regardless of
     * its source mode, so a model ahead of the catalog is refused instead of silently under-configured.
     *
     * @param model active feature model.
     * @param selectedFeatureIds validated selected feature ids.
     * @param environment resolved environment values.
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
        List<RemoteAnsibleEmissionPlan.PlannedFile> files = new ArrayList<>();
        List<RemoteAnsibleEmissionPlan.PlannedVaultReference> vaultReferences = new ArrayList<>();
        List<SelectedBoundFeature> boundFeatures = selectedBoundFeatures(model, selectedFeatureIds);

        files.add(new RemoteAnsibleEmissionPlan.PlannedFile(HOSTS_FILE, hostsContent(targetGroup, environment, databaseBinding, ciBinding, boundFeatures)));
        files.add(new RemoteAnsibleEmissionPlan.PlannedFile(GROUP_VARS_DIR + targetGroup + "/main.yml", targetMainContent(environment)));
        String secretsPath = GROUP_VARS_DIR + targetGroup + "/secrets.yml";
        files.add(new RemoteAnsibleEmissionPlan.PlannedFile(secretsPath, targetSecretsContent(environment)));
        files.add(new RemoteAnsibleEmissionPlan.PlannedFile(GROUP_VARS_DIR + "artemistests_common_config.yml", commonConfigContent(environment)));
        files.add(plannedGroupFile(databaseBinding, environment));
        files.add(plannedGroupFile(ciBinding, environment));
        for (SelectedBoundFeature boundFeature : boundFeatures) {
            files.add(plannedGroupFile(boundFeature.binding(), environment));
        }

        for (AnsibleBindingCatalog.SecretEntry secret : catalog.secrets()) {
            vaultReferences.add(new RemoteAnsibleEmissionPlan.PlannedVaultReference(resolveVaultServerName(secret.vaultPath(), environment),
                    secret.vaultField(), secret.var(), secretsPath));
        }
        collectVaultReferences(ciBinding, environment, vaultReferences);
        for (SelectedBoundFeature boundFeature : boundFeatures) {
            collectVaultReferences(boundFeature.binding(), environment, vaultReferences);
        }

        List<RemoteAnsibleEmissionPlan.EnvironmentState> environmentStates = new ArrayList<>();
        for (RemoteEnvironmentValues.InputValue input : environment.inputs()) {
            environmentStates.add(new RemoteAnsibleEmissionPlan.EnvironmentState(input.input(),
                    input.provided() ? RemoteAnsibleEmissionPlan.ENVIRONMENT_PROVIDED : RemoteAnsibleEmissionPlan.ENVIRONMENT_PENDING));
        }

        return new RemoteAnsibleEmissionPlan(targetGroup, files, vaultReferences, classifications, environmentStates);
    }

    /**
     * Classifies every selectable feature of the model against the catalog and enforces the fail-closed contract:
     * unclassified features and inexpressible selection states abort the run before any content is produced.
     *
     * @param model active feature model.
     * @param selectedFeatureIds selected feature ids.
     * @return classification results in model order.
     * @throws ArtifactGenerationException if a feature is unclassified or a selection state is unsupported.
     */
    public List<RemoteAnsibleEmissionPlan.FeatureClassificationResult> classify(FeatureModel model, Set<String> selectedFeatureIds) {
        Set<String> optionalFeatureIds = optionalFeatureIds(model);
        List<RemoteAnsibleEmissionPlan.FeatureClassificationResult> classifications = new ArrayList<>();
        for (FeatureNode feature : model.features()) {
            if (!SELECTABLE_KINDS.contains(feature.kind())) {
                continue;
            }
            AnsibleBindingCatalog.FeatureBinding binding = bindingFor(feature.id());
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
     * Emits a value-gated block: every declared gated field must be non-empty, otherwise nothing is emitted. This
     * mirrors the collection's template guard, which drops the whole block unless all fields are set.
     *
     * @param binding value-gated feature binding.
     * @return rendered block lines, or an empty list when any gated field is missing or blank.
     */
    public List<String> valueGatedBlockLines(AnsibleBindingCatalog.FeatureBinding binding) {
        List<AnsibleBindingCatalog.GatedField> gatedFields = binding.gatedFields();
        if (gatedFields.isEmpty()) {
            return List.of();
        }
        for (AnsibleBindingCatalog.GatedField field : gatedFields) {
            if (field.line() == null || field.line().isBlank()) {
                return List.of();
            }
        }
        List<String> lines = new ArrayList<>(binding.prefixLines());
        for (AnsibleBindingCatalog.GatedField field : gatedFields) {
            lines.add(field.line());
        }
        lines.addAll(binding.suffixLines());
        return List.copyOf(lines);
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
        if (AnsibleBindingCatalog.UNSUPPORTED_WHEN_SELECTED.equals(direction) && selected) {
            throw ArtifactGenerationException.remoteAnsibleUnsupportedFeature(featureId, detail);
        }
        if (AnsibleBindingCatalog.UNSUPPORTED_WHEN_DESELECTED.equals(direction) && optional && !selected) {
            throw ArtifactGenerationException.remoteAnsibleUnsupportedFeature(featureId, detail);
        }
    }

    /**
     * Finds the classification of a feature id across the feature and technical sections.
     *
     * @param featureId feature id.
     * @return binding, or {@code null} if the catalog does not classify the feature.
     */
    private AnsibleBindingCatalog.FeatureBinding bindingFor(String featureId) {
        AnsibleBindingCatalog.FeatureBinding binding = catalog.features().get(featureId);
        if (binding != null) {
            return binding;
        }
        binding = catalog.technical().database().get(featureId);
        if (binding != null) {
            return binding;
        }
        return catalog.technical().ciProvider().get(featureId);
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
     * Collects the selected bound functional features with their emitted blocks, in model order. A value-gated
     * feature whose block resolves to nothing is skipped entirely (no file, no membership).
     *
     * @param model active feature model.
     * @param selectedFeatureIds selected feature ids.
     * @return selected bound features that emit a group values file.
     */
    private List<SelectedBoundFeature> selectedBoundFeatures(FeatureModel model, Set<String> selectedFeatureIds) {
        List<SelectedBoundFeature> boundFeatures = new ArrayList<>();
        for (FeatureNode feature : model.features()) {
            if (!selectedFeatureIds.contains(feature.id())) {
                continue;
            }
            AnsibleBindingCatalog.FeatureBinding binding = catalog.features().get(feature.id());
            if (binding == null || !AnsibleBindingCatalog.BINDING_BOUND.equals(binding.binding())) {
                continue;
            }
            List<String> lines = AnsibleBindingCatalog.GATING_VALUE_GATED.equals(binding.gating()) ? valueGatedBlockLines(binding) : binding.lines();
            if (!lines.isEmpty()) {
                boundFeatures.add(new SelectedBoundFeature(feature.id(), binding, lines));
            }
        }
        return boundFeatures;
    }

    /**
     * Renders the inventory hosts file: the target group with its host line, then one children section per wired
     * group in package order.
     *
     * @param targetGroup inventory group name.
     * @param environment resolved environment values.
     * @param databaseBinding selected database binding.
     * @param ciBinding selected CI-provider binding.
     * @param boundFeatures selected bound features that emit files.
     * @return rendered hosts content.
     */
    private String hostsContent(String targetGroup, RemoteEnvironmentValues environment, AnsibleBindingCatalog.FeatureBinding databaseBinding,
            AnsibleBindingCatalog.FeatureBinding ciBinding, List<SelectedBoundFeature> boundFeatures) {
        List<String> memberships = new ArrayList<>(List.of("artemistests", "artemistests_common_config", databaseBinding.membership(), ciBinding.membership()));
        for (SelectedBoundFeature boundFeature : boundFeatures) {
            memberships.add(boundFeature.binding().membership());
        }
        StringBuilder content = new StringBuilder();
        content.append('[').append(targetGroup).append("]\n");
        content.append(environment.valueOf(RemoteEnvironmentValues.INPUT_SERVER_HOSTNAME)).append('\n');
        for (String membership : new LinkedHashSet<>(memberships)) {
            content.append('\n').append('[').append(membership).append(":children]\n").append(targetGroup).append('\n');
        }
        return content.toString();
    }

    /**
     * Renders the target-group main values file from the environment entries of the target file.
     *
     * @param environment resolved environment values.
     * @return rendered file content.
     */
    private String targetMainContent(RemoteEnvironmentValues environment) {
        List<String> lines = new ArrayList<>();
        lines.add("---");
        for (AnsibleBindingCatalog.EnvironmentEntry entry : catalog.environment()) {
            if (AnsibleBindingCatalog.FILE_TARGET_MAIN.equals(entry.file())) {
                lines.addAll(renderEnvironmentLines(entry, environment));
            }
        }
        return String.join("\n", lines);
    }

    /**
     * Renders the target-group secrets file: every value is a vault lookup expression, never a secret value.
     *
     * @param environment resolved environment values.
     * @return rendered file content.
     */
    private String targetSecretsContent(RemoteEnvironmentValues environment) {
        List<String> lines = new ArrayList<>();
        lines.add("---");
        lines.add("# Secret values are never stored in this package; every value below is a Vault lookup");
        lines.add("# expression. See README.md for the Vault setup and the no-Vault alternative.");
        for (AnsibleBindingCatalog.SecretEntry secret : catalog.secrets()) {
            String path = resolveVaultServerName(secret.vaultPath(), environment);
            lines.add(secret.var() + ": \"{{ lookup('hashi_vault', '" + path + "').get('" + secret.vaultField() + "') }}\"");
        }
        return String.join("\n", lines);
    }

    /**
     * Renders the common configuration values file by merging baseline and environment entries in catalog order,
     * separating groups with blank lines.
     *
     * @param environment resolved environment values.
     * @return rendered file content.
     */
    private String commonConfigContent(RemoteEnvironmentValues environment) {
        List<CommonConfigEntry> entries = new ArrayList<>();
        for (AnsibleBindingCatalog.BaselineEntry entry : catalog.baseline()) {
            entries.add(new CommonConfigEntry(entry.order(), entry.group(), entry.lines()));
        }
        for (AnsibleBindingCatalog.EnvironmentEntry entry : catalog.environment()) {
            if (AnsibleBindingCatalog.FILE_COMMON_CONFIG.equals(entry.file())) {
                entries.add(new CommonConfigEntry(entry.order(), entry.group(), renderEnvironmentLines(entry, environment)));
            }
        }
        entries.sort(java.util.Comparator.comparingInt(CommonConfigEntry::order));

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
     * Renders the lines of an environment entry with its resolved value, escaped for the YAML double-quoted scalar
     * the catalog lines place it in.
     *
     * @param entry environment entry.
     * @param environment resolved environment values.
     * @return rendered lines.
     */
    private List<String> renderEnvironmentLines(AnsibleBindingCatalog.EnvironmentEntry entry, RemoteEnvironmentValues environment) {
        String value = RemoteEnvironmentValues.yamlDoubleQuoted(environment.valueOf(entry.input()));
        List<String> rendered = new ArrayList<>();
        for (String line : entry.lines()) {
            rendered.add(line.replace(VALUE_TOKEN, value));
        }
        return rendered;
    }

    /**
     * Renders one bound group values file with the vault server name resolved.
     *
     * @param binding bound binding.
     * @param environment resolved environment values.
     * @return planned group values file.
     */
    private RemoteAnsibleEmissionPlan.PlannedFile plannedGroupFile(AnsibleBindingCatalog.FeatureBinding binding, RemoteEnvironmentValues environment) {
        List<String> lines = AnsibleBindingCatalog.GATING_VALUE_GATED.equals(binding.gating()) ? valueGatedBlockLines(binding) : binding.lines();
        List<String> rendered = new ArrayList<>();
        for (String line : lines) {
            rendered.add(resolveVaultServerName(line, environment));
        }
        return new RemoteAnsibleEmissionPlan.PlannedFile(GROUP_VARS_DIR + binding.groupVarsFile(), String.join("\n", rendered));
    }

    /**
     * Collects the resolved vault references of one bound binding.
     *
     * @param binding bound binding.
     * @param environment resolved environment values.
     * @param vaultReferences accumulating reference list.
     */
    private void collectVaultReferences(AnsibleBindingCatalog.FeatureBinding binding, RemoteEnvironmentValues environment,
            List<RemoteAnsibleEmissionPlan.PlannedVaultReference> vaultReferences) {
        for (AnsibleBindingCatalog.VaultReference reference : binding.vaultReferences()) {
            vaultReferences.add(new RemoteAnsibleEmissionPlan.PlannedVaultReference(resolveVaultServerName(reference.path(), environment), reference.field(),
                    reference.consumer(), GROUP_VARS_DIR + binding.groupVarsFile()));
        }
    }

    /**
     * Substitutes the vault-server-name token in a line or path.
     *
     * @param text line or path text.
     * @param environment resolved environment values.
     * @return text with the token replaced.
     */
    private String resolveVaultServerName(String text, RemoteEnvironmentValues environment) {
        return text.replace(VAULT_SERVER_NAME_TOKEN, environment.valueOf(RemoteEnvironmentValues.INPUT_VAULT_SERVER_NAME));
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
            if (RELATION_TYPE_OPTIONAL.equals(relation.relationType())) {
                optionalIds.add(relation.childId());
            }
        }
        return optionalIds;
    }

    /**
     * One selected bound functional feature with its emitted block.
     *
     * @param featureId feature id.
     * @param binding bound binding.
     * @param lines emitted block lines.
     */
    private record SelectedBoundFeature(String featureId, AnsibleBindingCatalog.FeatureBinding binding, List<String> lines) {
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
