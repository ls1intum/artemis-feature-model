package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigurationDefault;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;

/** Joins module constants, helper accessors, conditions, defaults, admin membership, and texts into module facts. */
final class ModuleCandidateAssembler {

    private static final String MODULE_CONSTANT_PREFIX = ArtemisSourceConventions.Symbols.MODULE_FEATURE_PREFIX;

    private static final String PROPERTY_CONSTANT_SUFFIX = ArtemisSourceConventions.Naming.ENABLED_PROPERTY_CONSTANT_SUFFIX;

    private static final String CONDITION_CLASS_SUFFIX = ArtemisSourceConventions.Naming.CONDITION_CLASS_SUFFIX;

    /**
     * Module assembly facts required by the relation collaborator.
     *
     * @param candidates assembled module candidates.
     * @param conditionPropertyKeys resolved property keys per condition class.
     * @param candidateIdsByConfigKey module candidate id per owned config key.
     * @param candidateIdsByConditionStem module candidate id whose normalized stem matches a condition class.
     */
    record Result(List<FeatureCandidate> candidates, Map<String, Set<String>> conditionPropertyKeys, Map<String, String> candidateIdsByConfigKey,
            Map<String, String> candidateIdsByConditionStem) {

        /**
         * Resolves the module that owns a configuration key.
         *
         * @param configKey dotted configuration key.
         * @return candidate id, or null.
         */
        String candidateIdForConfigKey(String configKey) {
            return candidateIdsByConfigKey.get(configKey);
        }

        /**
         * Resolves the module whose normalized stem matches a condition class name.
         *
         * @param conditionClass condition class name.
         * @return candidate id, or null.
         */
        String candidateIdForCondition(String conditionClass) {
            return candidateIdsByConditionStem.get(conditionClass);
        }
    }

    /** Mutable module draft owned by one invocation only. */
    private static final class ModuleDraft {

        private String displayId;

        private BackendConstantScan.ScannedConstant backendModuleConstant;

        private BackendConstantScan.ScannedConstant propertyConstant;

        private FrontendConstantScan.ScannedFrontendConstant frontendConstant;

        private ConditionClassScan.ScannedCondition ownCondition;

        private String configKey;

        private boolean enumeratedByBackend;

        private boolean displayedOnAdminPage;

        private String documentationUrl;

        private FeatureI18nScan.FeatureTexts texts;

        private String candidateId() {
            return FeatureCandidate.NAMESPACE_MODULE + displayId;
        }
    }

    /**
     * Assembles the module family for one invocation.
     *
     * @param input complete candidate-assembly input.
     * @param context invocation-local evidence and diagnostic context.
     * @return module candidates and relation joins.
     */
    Result assemble(CandidateAssemblyInput input, CandidateAssemblyContext context) {
        return new Invocation(input, context).assemble();
    }

    /** Invocation-local module join state and algorithms. */
    private static final class Invocation {

        private final CandidateAssemblyInput input;

        private final CandidateAssemblyContext context;

        private final Map<String, ModuleDraft> moduleDraftsByStem = new LinkedHashMap<>();

        private Invocation(CandidateAssemblyInput input, CandidateAssemblyContext context) {
            this.input = input;
            this.context = context;
        }

        /**
         * Executes the module joins in their established order.
         *
         * @return module candidates and relation lookup facts.
         */
        private Result assemble() {
            Map<String, Set<String>> accessorPropertyKeys = resolveAccessorPropertyKeys();
            Map<String, Set<String>> conditionPropertyKeys = resolveConditionPropertyKeys(accessorPropertyKeys);
            buildModuleDrafts(conditionPropertyKeys);
            joinEnumeration();
            joinAdminPageAndI18n();
            joinDefaults();
            reportMirrorAndAsymmetry();
            return new Result(List.copyOf(emitCandidates()), conditionPropertyKeys, candidateIdsByConfigKey(), candidateIdsByCondition());
        }

        /** Resolves every config-helper accessor to the property keys it reads, following nested accessors. */
        private Map<String, Set<String>> resolveAccessorPropertyKeys() {
            Map<String, ConfigHelperScan.ScannedAccessor> accessorsByName = new LinkedHashMap<>();
            input.configHelper().accessors().forEach(accessor -> accessorsByName.putIfAbsent(accessor.name(), accessor));
            Map<String, Set<String>> resolved = new LinkedHashMap<>();
            accessorsByName.keySet().forEach(name -> resolved.put(name, resolveAccessor(name, accessorsByName, new LinkedHashSet<>())));
            return resolved;
        }

        /** Resolves one accessor recursively while guarding against call cycles. */
        private Set<String> resolveAccessor(String name, Map<String, ConfigHelperScan.ScannedAccessor> accessorsByName, Set<String> visiting) {
            Set<String> keys = new LinkedHashSet<>();
            ConfigHelperScan.ScannedAccessor accessor = accessorsByName.get(name);
            if (accessor == null || !visiting.add(name)) {
                return keys;
            }
            for (String constantName : accessor.propertyConstantNames()) {
                findConstant(constantName).ifPresent(constant -> keys.add(constant.value()));
            }
            for (String nested : accessor.nestedAccessorNames()) {
                keys.addAll(resolveAccessor(nested, accessorsByName, visiting));
            }
            visiting.remove(name);
            return keys;
        }

        /** Resolves every condition class to the property keys used by its matches implementation. */
        private Map<String, Set<String>> resolveConditionPropertyKeys(Map<String, Set<String>> accessorPropertyKeys) {
            Map<String, Set<String>> resolved = new LinkedHashMap<>();
            for (ConditionClassScan.ScannedCondition condition : input.conditions().conditions()) {
                Set<String> keys = new LinkedHashSet<>();
                condition.propertyConstantNames().forEach(constantName -> findConstant(constantName).ifPresent(constant -> keys.add(constant.value())));
                keys.addAll(condition.literalPropertyKeys());
                condition.accessorNames().forEach(accessorName -> keys.addAll(accessorPropertyKeys.getOrDefault(accessorName, Set.of())));
                resolved.put(condition.className(), keys);
            }
            return resolved;
        }

        /** Builds invocation-local drafts from constants and conditions. */
        private void buildModuleDrafts(Map<String, Set<String>> conditionPropertyKeys) {
            for (BackendConstantScan.ScannedConstant constant : input.backendConstants().constants()) {
                if (constant.name().startsWith(MODULE_CONSTANT_PREFIX)) {
                    ModuleDraft draft = draftFor(constant.value(), constant.value());
                    draft.backendModuleConstant = constant;
                    context.addEvidence(draft.candidateId(), EvidenceItem.KIND_BACKEND_CONSTANT, input.backendConstants().file(), constant.line(), constant.name(),
                            null);
                }
            }
            for (FrontendConstantScan.ScannedFrontendConstant constant : input.frontendConstants().constants()) {
                if (constant.name().startsWith(MODULE_CONSTANT_PREFIX)) {
                    ModuleDraft draft = draftFor(constant.value(), constant.value());
                    draft.frontendConstant = constant;
                    context.addEvidence(draft.candidateId(), EvidenceItem.KIND_FRONTEND_CONSTANT, input.frontendConstants().file(), constant.line(), constant.name(),
                            null);
                }
            }
            for (BackendConstantScan.ScannedConstant constant : input.backendConstants().constants()) {
                if (constant.name().endsWith(PROPERTY_CONSTANT_SUFFIX)) {
                    String stem = normalize(constant.name().substring(0, constant.name().length() - PROPERTY_CONSTANT_SUFFIX.length()));
                    ModuleDraft draft = draftFor(stem, stem);
                    draft.propertyConstant = constant;
                    draft.configKey = constant.value();
                    context.addEvidence(draft.candidateId(), EvidenceItem.KIND_BACKEND_CONSTANT, input.backendConstants().file(), constant.line(), constant.name(),
                            null);
                }
            }
            for (ConditionClassScan.ScannedCondition condition : input.conditions().conditions()) {
                attachCondition(condition, conditionPropertyKeys.getOrDefault(condition.className(), Set.of()));
            }
        }

        /** Attaches an owning condition, creating a condition-only draft when it resolves one key. */
        private void attachCondition(ConditionClassScan.ScannedCondition condition, Set<String> propertyKeys) {
            String stem = normalize(stripSuffix(condition.className(), CONDITION_CLASS_SUFFIX));
            ModuleDraft draft = moduleDraftsByStem.get(stem);
            if (draft == null && propertyKeys.size() == 1) {
                String onlyKey = propertyKeys.iterator().next();
                draft = findDraftByConfigKey(onlyKey);
                if (draft == null) {
                    draft = draftFor(stem, stem);
                    draft.configKey = onlyKey;
                }
            }
            if (draft == null) {
                return;
            }
            draft.ownCondition = condition;
            context.addEvidence(draft.candidateId(), EvidenceItem.KIND_CONDITION_CLASS, condition.file(), condition.line(), condition.className(), null);
        }

        /** Joins the backend enabled-feature enumeration. */
        private void joinEnumeration() {
            for (ConfigHelperScan.EnumerationEntry entry : input.configHelper().enumerationEntries()) {
                findConstant(entry.constantName()).ifPresent(constant -> {
                    ModuleDraft draft = draftFor(constant.value(), constant.value());
                    draft.enumeratedByBackend = true;
                    context.addEvidence(draft.candidateId(), EvidenceItem.KIND_BACKEND_ENUMERATION, input.configHelper().file(), entry.line(), entry.constantName(),
                            null);
                });
            }
        }

        /** Joins admin display membership, documentation links, and translated module texts. */
        private void joinAdminPageAndI18n() {
            Map<String, String> frontendValuesByName = new LinkedHashMap<>();
            input.frontendConstants().constants().forEach(constant -> frontendValuesByName.putIfAbsent(constant.name(), constant.value()));
            for (AdminPageScan.MembershipEntry entry : input.adminPage().displayedModuleFeatures()) {
                String value = frontendValuesByName.get(entry.identifier());
                ModuleDraft draft = value == null ? null : moduleDraftsByStem.get(normalize(value));
                if (draft != null) {
                    draft.displayedOnAdminPage = true;
                    context.addEvidence(draft.candidateId(), EvidenceItem.KIND_ADMIN_PAGE, input.adminPage().file(), entry.line(), entry.identifier(),
                            "display membership");
                }
            }
            for (AdminPageScan.DocumentationEntry entry : input.adminPage().documentationEntries()) {
                if (entry.identifier().startsWith(MODULE_CONSTANT_PREFIX)) {
                    String value = frontendValuesByName.get(entry.identifier());
                    ModuleDraft draft = value == null ? null : moduleDraftsByStem.get(normalize(value));
                    if (draft != null) {
                        draft.documentationUrl = entry.url();
                        context.addEvidence(draft.candidateId(), EvidenceItem.KIND_ADMIN_PAGE, input.adminPage().file(), entry.line(), entry.identifier(), entry.url());
                    }
                }
            }
            input.featureTexts().moduleTexts().forEach((moduleId, texts) -> {
                ModuleDraft draft = moduleDraftsByStem.get(normalize(moduleId));
                if (draft != null) {
                    draft.texts = texts;
                    context.addEvidence(draft.candidateId(), EvidenceItem.KIND_I18N, input.featureTexts().file(), null,
                            "artemisApp.features.modules." + moduleId, null);
                }
            });
        }

        /** Joins YAML defaults and config-helper accessor declarations. */
        private void joinDefaults() {
            for (ModuleDraft draft : moduleDraftsByStem.values()) {
                if (draft.configKey == null) {
                    continue;
                }
                ExtractedConfigurationDefault occurrence = input.configurationDefaults().preferredOccurrence(draft.configKey);
                if (occurrence != null) {
                    context.addEvidence(draft.candidateId(), EvidenceItem.KIND_YAML_DEFAULT, occurrence.file(), occurrence.line(), draft.configKey,
                            String.valueOf(occurrence.value()));
                }
            }
            Map<String, ConfigHelperScan.ScannedAccessor> accessorsByName = new LinkedHashMap<>();
            input.configHelper().accessors().forEach(accessor -> accessorsByName.putIfAbsent(accessor.name(), accessor));
            accessorsByName.values().forEach(accessor -> accessor.propertyConstantNames().stream().findFirst().flatMap(this::findConstant).ifPresent(constant -> {
                ModuleDraft draft = findDraftByConfigKey(constant.value());
                if (draft != null) {
                    context.addEvidence(draft.candidateId(), EvidenceItem.KIND_CONFIG_HELPER_ACCESSOR, input.configHelper().file(), accessor.line(),
                            accessor.name(), null);
                }
            }));
        }

        /** Reports module mirror mismatches and internal enabled-property asymmetry. */
        private void reportMirrorAndAsymmetry() {
            Set<String> backendValues = new LinkedHashSet<>();
            Set<String> frontendValues = new LinkedHashSet<>();
            input.backendConstants().constants().stream().filter(constant -> constant.name().startsWith(MODULE_CONSTANT_PREFIX))
                    .forEach(constant -> backendValues.add(constant.value()));
            input.frontendConstants().constants().stream().filter(constant -> constant.name().startsWith(MODULE_CONSTANT_PREFIX))
                    .forEach(constant -> frontendValues.add(constant.value()));
            for (String value : backendValues) {
                if (!frontendValues.contains(value)) {
                    context.addItem(ReportItem.warning(ReportItem.CODE_FE_BE_MIRROR_MISMATCH, FeatureCandidate.NAMESPACE_MODULE + value,
                            "Module feature constant for '" + value + "' exists in the backend but has no frontend MODULE_FEATURE_ mirror."));
                }
            }
            for (String value : frontendValues) {
                if (!backendValues.contains(value)) {
                    ModuleDraft draft = moduleDraftsByStem.get(normalize(value));
                    String enumerationHint = draft != null && draft.enumeratedByBackend
                            ? " The backend still enumerates the id at runtime through a non-MODULE_FEATURE constant."
                            : "";
                    context.addItem(ReportItem.warning(ReportItem.CODE_FE_BE_MIRROR_MISMATCH, FeatureCandidate.NAMESPACE_MODULE + value,
                            "Module feature constant for '" + value + "' exists only in the frontend." + enumerationHint));
                }
            }
            Set<String> moduleStems = new LinkedHashSet<>();
            input.backendConstants().constants().stream().filter(constant -> constant.name().startsWith(MODULE_CONSTANT_PREFIX))
                    .forEach(constant -> moduleStems.add(normalize(constant.value())));
            for (BackendConstantScan.ScannedConstant constant : input.backendConstants().constants()) {
                if (constant.name().endsWith(PROPERTY_CONSTANT_SUFFIX)) {
                    String stem = normalize(stripSuffix(constant.name(), PROPERTY_CONSTANT_SUFFIX));
                    if (!moduleStems.contains(stem)) {
                        context.addItem(ReportItem.info(ReportItem.CODE_MODULE_CONSTANT_ASYMMETRY, constant.name(),
                                "Enabled property constant '" + constant.name() + "' has no matching backend MODULE_FEATURE_ constant."));
                    }
                }
            }
        }

        /** Emits module candidates in draft insertion order. */
        private List<FeatureCandidate> emitCandidates() {
            List<FeatureCandidate> candidates = new ArrayList<>();
            for (ModuleDraft draft : moduleDraftsByStem.values()) {
                Object defaultValue = draft.configKey == null ? null : valueOf(input.configurationDefaults().preferredOccurrence(draft.configKey));
                String backendConstant = draft.backendModuleConstant != null ? draft.backendModuleConstant.name()
                        : draft.propertyConstant != null ? draft.propertyConstant.name() : null;
                candidates.add(new FeatureCandidate(draft.candidateId(), FeatureCandidate.KIND_MODULE_FEATURE,
                        draft.texts == null ? null : draft.texts.name(), moduleDescription(draft), null, draft.configKey, defaultValue, backendConstant,
                        draft.frontendConstant == null ? null : draft.frontendConstant.name(),
                        draft.ownCondition == null ? null : draft.ownCondition.className(), null, draft.enumeratedByBackend, draft.displayedOnAdminPage,
                        draft.documentationUrl));
            }
            return candidates;
        }

        /** Chooses i18n, property-constant, module-constant, then condition description precedence. */
        private String moduleDescription(ModuleDraft draft) {
            if (draft.texts != null && draft.texts.description() != null) {
                return draft.texts.description();
            }
            if (draft.propertyConstant != null && draft.propertyConstant.javadoc() != null) {
                return draft.propertyConstant.javadoc();
            }
            if (draft.backendModuleConstant != null && draft.backendModuleConstant.javadoc() != null) {
                return draft.backendModuleConstant.javadoc();
            }
            return draft.ownCondition == null ? null : draft.ownCondition.javadoc();
        }

        /** Builds the first-owning module lookup for configuration keys. */
        private Map<String, String> candidateIdsByConfigKey() {
            Map<String, String> candidateIds = new LinkedHashMap<>();
            for (ModuleDraft draft : moduleDraftsByStem.values()) {
                if (draft.configKey != null) {
                    candidateIds.putIfAbsent(draft.configKey, draft.candidateId());
                }
            }
            return candidateIds;
        }

        /** Builds the normalized-stem module lookup used by conditions. */
        private Map<String, String> candidateIdsByCondition() {
            Map<String, String> candidateIds = new LinkedHashMap<>();
            for (ConditionClassScan.ScannedCondition condition : input.conditions().conditions()) {
                ModuleDraft draft = moduleDraftsByStem.get(normalize(stripSuffix(condition.className(), CONDITION_CLASS_SUFFIX)));
                if (draft != null) {
                    candidateIds.put(condition.className(), draft.candidateId());
                }
            }
            return candidateIds;
        }

        /** Returns or creates the draft for a normalized source stem. */
        private ModuleDraft draftFor(String value, String displayId) {
            ModuleDraft draft = moduleDraftsByStem.computeIfAbsent(normalize(value), unused -> new ModuleDraft());
            if (draft.displayId == null || (!draft.displayId.equals(displayId) && displayId.contains("-"))) {
                draft.displayId = displayId;
            }
            return draft;
        }

        /** Finds the first module draft owning a config key. */
        private ModuleDraft findDraftByConfigKey(String configKey) {
            for (ModuleDraft draft : moduleDraftsByStem.values()) {
                if (configKey.equals(draft.configKey)) {
                    return draft;
                }
            }
            return null;
        }

        /** Finds one backend constant by exact name. */
        private Optional<BackendConstantScan.ScannedConstant> findConstant(String name) {
            return input.backendConstants().constants().stream().filter(constant -> constant.name().equals(name)).findFirst();
        }

        /** Extracts the scalar value of an occurrence. */
        private Object valueOf(ExtractedConfigurationDefault occurrence) {
            return occurrence == null ? null : occurrence.value();
        }
    }

    /** Normalizes symbol separators for source joining. */
    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    /** Strips a suffix when present. */
    private static String stripSuffix(String value, String suffix) {
        return value.endsWith(suffix) ? value.substring(0, value.length() - suffix.length()) : value;
    }
}
