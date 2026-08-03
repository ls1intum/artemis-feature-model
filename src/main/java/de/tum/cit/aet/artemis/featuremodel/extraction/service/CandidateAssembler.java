package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.RelationCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;

/**
 * Joins the raw anchor scans into namespaced feature candidates with evidence, relation candidates from composite
 * conditions, and structural diagnostics. Anchors are joined by normalized symbol stems, never by file paths, so
 * moved files keep their identity.
 */
class CandidateAssembler {

    private static final String MODULE_CONSTANT_PREFIX = ArtemisSourceConventions.Symbols.MODULE_FEATURE_PREFIX;

    private static final String PROFILE_CONSTANT_PREFIX = ArtemisSourceConventions.Symbols.PROFILE_CONSTANT_PREFIX;

    private static final String PROPERTY_CONSTANT_SUFFIX = ArtemisSourceConventions.Naming.ENABLED_PROPERTY_CONSTANT_SUFFIX;

    private static final String CONDITION_CLASS_SUFFIX = ArtemisSourceConventions.Naming.CONDITION_CLASS_SUFFIX;

    private static final String TOGGLE_DOC_IDENTIFIER_PREFIX = ArtemisSourceConventions.Symbols.FRONTEND_TOGGLE_REFERENCE_PREFIX;

    private static final String RELATION_ID_PREFIX = "relation:";

    /**
     * Assembly result over all scans.
     *
     * @param candidates feature candidates sorted by id.
     * @param evidence evidence items sorted by candidate id, file, line, kind, and symbol.
     * @param relationCandidates relation candidates sorted by id.
     * @param items structural report items produced during assembly.
     */
    record Result(List<FeatureCandidate> candidates, List<EvidenceItem> evidence, List<RelationCandidate> relationCandidates, List<ReportItem> items) {
    }

    /** Mutable module feature draft merged from all anchor sources before candidate emission. */
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

    private final Map<String, ModuleDraft> moduleDraftsByStem = new LinkedHashMap<>();

    private final List<EvidenceItem> evidence = new ArrayList<>();

    private final List<ReportItem> items = new ArrayList<>();

    private BackendConstantScan.Result constantScan = BackendConstantScan.Result.empty();

    private ConfigHelperScan.Result configHelperScan = ConfigHelperScan.Result.empty();

    private ConditionClassScan.Result conditionScan = ConditionClassScan.Result.empty();

    private BackendFeatureEnumScan.Result backendToggleScan = BackendFeatureEnumScan.Result.empty();

    private FrontendConstantScan.Result frontendConstantScan = FrontendConstantScan.Result.empty();

    private FrontendToggleEnumScan.Result frontendToggleScan = FrontendToggleEnumScan.Result.empty();

    private AdminPageScan.Result adminPageScan = AdminPageScan.Result.empty();

    private FeatureI18nScan.Result i18nScan = FeatureI18nScan.Result.empty();

    private YamlConfigScan.Result yamlScan = YamlConfigScan.Result.empty();

    private ComposeFileScan.Result composeScan = ComposeFileScan.Result.empty();

    private UsageEvidenceScan.Result usageScan = UsageEvidenceScan.Result.empty();

    /**
     * Assembles candidates, evidence, relations, and structural diagnostics from the raw scans.
     *
     * @param source Artemis source repository, used for profile YAML existence evidence.
     * @param constantScan backend constants scan result.
     * @param configHelperScan config helper scan result.
     * @param conditionScan condition class scan result.
     * @param backendToggleScan backend feature enum scan result.
     * @param frontendConstantScan frontend constants scan result.
     * @param frontendToggleScan frontend toggle enum scan result.
     * @param adminPageScan admin page scan result.
     * @param i18nScan i18n scan result.
     * @param yamlScan configuration defaults scan result.
     * @param composeScan compose file scan result.
     * @param usageScan usage evidence scan result.
     * @return assembly result with deterministic ordering.
     */
    Result assemble(ArtemisSourceRepository source, BackendConstantScan.Result constantScan, ConfigHelperScan.Result configHelperScan,
            ConditionClassScan.Result conditionScan, BackendFeatureEnumScan.Result backendToggleScan, FrontendConstantScan.Result frontendConstantScan,
            FrontendToggleEnumScan.Result frontendToggleScan, AdminPageScan.Result adminPageScan, FeatureI18nScan.Result i18nScan, YamlConfigScan.Result yamlScan,
            ComposeFileScan.Result composeScan, UsageEvidenceScan.Result usageScan) {
        this.constantScan = constantScan;
        this.configHelperScan = configHelperScan;
        this.conditionScan = conditionScan;
        this.backendToggleScan = backendToggleScan;
        this.frontendConstantScan = frontendConstantScan;
        this.frontendToggleScan = frontendToggleScan;
        this.adminPageScan = adminPageScan;
        this.i18nScan = i18nScan;
        this.yamlScan = yamlScan;
        this.composeScan = composeScan;
        this.usageScan = usageScan;

        Map<String, Set<String>> accessorPropertyKeys = resolveAccessorPropertyKeys();
        Map<String, Set<String>> conditionPropertyKeys = resolveConditionPropertyKeys(accessorPropertyKeys);
        buildModuleDrafts(conditionPropertyKeys);
        joinEnumeration();
        joinAdminPageAndI18nForModules();
        joinModuleDefaults(accessorPropertyKeys);
        reportModuleMirrorAndAsymmetry();

        List<FeatureCandidate> candidates = new ArrayList<>();
        candidates.addAll(emitModuleCandidates());
        List<RelationCandidate> relations = emitRelationCandidates(conditionPropertyKeys);
        candidates.addAll(emitToggleCandidates());
        candidates.addAll(emitProfileCandidates(source));
        candidates.addAll(emitInfrastructureCandidates());
        candidates.addAll(emitConfigKeyCandidates());
        attachConditionalUsageEvidence(conditionPropertyKeys);

        candidates.sort(Comparator.comparing(FeatureCandidate::id));
        evidence.sort(Comparator.comparing(EvidenceItem::candidateId).thenComparing(item -> item.file() == null ? "" : item.file())
                .thenComparing(item -> item.line() == null ? Integer.MAX_VALUE : item.line()).thenComparing(EvidenceItem::kind)
                .thenComparing(item -> item.symbol() == null ? "" : item.symbol()));
        return new Result(List.copyOf(candidates), List.copyOf(evidence), relations, List.copyOf(items));
    }

    /**
     * Resolves every config helper accessor to the set of property keys it reads, following nested accessor calls.
     *
     * @return property keys per accessor name, in call order.
     */
    private Map<String, Set<String>> resolveAccessorPropertyKeys() {
        Map<String, ConfigHelperScan.ScannedAccessor> accessorsByName = new LinkedHashMap<>();
        configHelperScan.accessors().forEach(accessor -> accessorsByName.putIfAbsent(accessor.name(), accessor));
        Map<String, Set<String>> resolved = new LinkedHashMap<>();
        accessorsByName.keySet().forEach(name -> resolved.put(name, resolveAccessor(name, accessorsByName, new LinkedHashSet<>())));
        return resolved;
    }

    /**
     * Recursively resolves one accessor to its property keys.
     *
     * @param name accessor name.
     * @param accessorsByName all scanned accessors.
     * @param visiting accessor names on the current resolution path, guarding against cycles.
     * @return property keys read by the accessor.
     */
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

    /**
     * Resolves every condition class to the property keys its {@code matches} implementation reads.
     *
     * @param accessorPropertyKeys resolved accessor property keys.
     * @return property keys per condition class name.
     */
    private Map<String, Set<String>> resolveConditionPropertyKeys(Map<String, Set<String>> accessorPropertyKeys) {
        Map<String, Set<String>> resolved = new LinkedHashMap<>();
        for (ConditionClassScan.ScannedCondition condition : conditionScan.conditions()) {
            Set<String> keys = new LinkedHashSet<>();
            condition.propertyConstantNames().forEach(constantName -> findConstant(constantName).ifPresent(constant -> keys.add(constant.value())));
            keys.addAll(condition.literalPropertyKeys());
            condition.accessorNames().forEach(accessorName -> keys.addAll(accessorPropertyKeys.getOrDefault(accessorName, Set.of())));
            resolved.put(condition.className(), keys);
        }
        return resolved;
    }

    /**
     * Builds the module drafts from backend constants, frontend constants, property constants, and condition classes.
     *
     * @param conditionPropertyKeys resolved condition property keys.
     */
    private void buildModuleDrafts(Map<String, Set<String>> conditionPropertyKeys) {
        for (BackendConstantScan.ScannedConstant constant : constantScan.constants()) {
            if (constant.name().startsWith(MODULE_CONSTANT_PREFIX)) {
                ModuleDraft draft = draftFor(constant.value(), constant.value());
                draft.backendModuleConstant = constant;
                addEvidence(draft.candidateId(), EvidenceItem.KIND_BACKEND_CONSTANT, constantScan.file(), constant.line(), constant.name(), null);
            }
        }
        for (FrontendConstantScan.ScannedFrontendConstant constant : frontendConstantScan.constants()) {
            if (constant.name().startsWith(MODULE_CONSTANT_PREFIX)) {
                ModuleDraft draft = draftFor(constant.value(), constant.value());
                draft.frontendConstant = constant;
                addEvidence(draft.candidateId(), EvidenceItem.KIND_FRONTEND_CONSTANT, frontendConstantScan.file(), constant.line(), constant.name(), null);
            }
        }
        for (BackendConstantScan.ScannedConstant constant : constantScan.constants()) {
            if (constant.name().endsWith(PROPERTY_CONSTANT_SUFFIX)) {
                String stem = normalize(constant.name().substring(0, constant.name().length() - PROPERTY_CONSTANT_SUFFIX.length()));
                ModuleDraft draft = draftFor(stem, stem);
                draft.propertyConstant = constant;
                draft.configKey = constant.value();
                addEvidence(draft.candidateId(), EvidenceItem.KIND_BACKEND_CONSTANT, constantScan.file(), constant.line(), constant.name(), null);
            }
        }
        for (ConditionClassScan.ScannedCondition condition : conditionScan.conditions()) {
            attachCondition(condition, conditionPropertyKeys.getOrDefault(condition.className(), Set.of()));
        }
    }

    /**
     * Attaches a condition class to its owning module draft, creating a draft for condition-only features. Composite
     * conditions without an own module stay unattached and only produce relation candidates.
     *
     * @param condition scanned condition class.
     * @param propertyKeys resolved property keys of the condition.
     */
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
        addEvidence(draft.candidateId(), EvidenceItem.KIND_CONDITION_CLASS, condition.file(), condition.line(), condition.className(), null);
    }

    /**
     * Marks modules enumerated by {@code getEnabledFeatures} and records the enumeration evidence.
     */
    private void joinEnumeration() {
        for (ConfigHelperScan.EnumerationEntry entry : configHelperScan.enumerationEntries()) {
            findConstant(entry.constantName()).ifPresent(constant -> {
                ModuleDraft draft = draftFor(constant.value(), constant.value());
                draft.enumeratedByBackend = true;
                addEvidence(draft.candidateId(), EvidenceItem.KIND_BACKEND_ENUMERATION, configHelperScan.file(), entry.line(), entry.constantName(), null);
            });
        }
    }

    /**
     * Joins admin page display membership, documentation links, and i18n texts onto module drafts.
     */
    private void joinAdminPageAndI18nForModules() {
        Map<String, String> frontendValuesByName = new LinkedHashMap<>();
        frontendConstantScan.constants().forEach(constant -> frontendValuesByName.putIfAbsent(constant.name(), constant.value()));
        for (AdminPageScan.MembershipEntry entry : adminPageScan.displayedModuleFeatures()) {
            String value = frontendValuesByName.get(entry.identifier());
            ModuleDraft draft = value == null ? null : moduleDraftsByStem.get(normalize(value));
            if (draft != null) {
                draft.displayedOnAdminPage = true;
                addEvidence(draft.candidateId(), EvidenceItem.KIND_ADMIN_PAGE, adminPageScan.file(), entry.line(), entry.identifier(), "display membership");
            }
        }
        for (AdminPageScan.DocumentationEntry entry : adminPageScan.documentationEntries()) {
            if (entry.identifier().startsWith(MODULE_CONSTANT_PREFIX)) {
                String value = frontendValuesByName.get(entry.identifier());
                ModuleDraft draft = value == null ? null : moduleDraftsByStem.get(normalize(value));
                if (draft != null) {
                    draft.documentationUrl = entry.url();
                    addEvidence(draft.candidateId(), EvidenceItem.KIND_ADMIN_PAGE, adminPageScan.file(), entry.line(), entry.identifier(), entry.url());
                }
            }
        }
        i18nScan.moduleTexts().forEach((moduleId, texts) -> {
            ModuleDraft draft = moduleDraftsByStem.get(normalize(moduleId));
            if (draft != null) {
                draft.texts = texts;
                addEvidence(draft.candidateId(), EvidenceItem.KIND_I18N, i18nScan.file(), null, "artemisApp.features.modules." + moduleId, null);
            }
        });
    }

    /**
     * Joins YAML default values and accessor declarations onto module drafts.
     *
     * @param accessorPropertyKeys resolved accessor property keys.
     */
    private void joinModuleDefaults(Map<String, Set<String>> accessorPropertyKeys) {
        for (ModuleDraft draft : moduleDraftsByStem.values()) {
            if (draft.configKey == null) {
                continue;
            }
            YamlConfigScan.KeyOccurrence occurrence = yamlScan.preferredOccurrence(draft.configKey);
            if (occurrence != null) {
                addEvidence(draft.candidateId(), EvidenceItem.KIND_YAML_DEFAULT, occurrence.file(), occurrence.line(), draft.configKey, String.valueOf(occurrence.value()));
            }
        }
        Map<String, ConfigHelperScan.ScannedAccessor> accessorsByName = new LinkedHashMap<>();
        configHelperScan.accessors().forEach(accessor -> accessorsByName.putIfAbsent(accessor.name(), accessor));
        accessorsByName.values().forEach(accessor -> accessor.propertyConstantNames().stream().findFirst().flatMap(this::findConstant).ifPresent(constant -> {
            ModuleDraft draft = findDraftByConfigKey(constant.value());
            if (draft != null) {
                addEvidence(draft.candidateId(), EvidenceItem.KIND_CONFIG_HELPER_ACCESSOR, configHelperScan.file(), accessor.line(), accessor.name(), null);
            }
        }));
    }

    /**
     * Reports frontend/backend module constant mirror mismatches and backend-internal constant asymmetries.
     */
    private void reportModuleMirrorAndAsymmetry() {
        Set<String> backendValues = new LinkedHashSet<>();
        Set<String> frontendValues = new LinkedHashSet<>();
        constantScan.constants().stream().filter(constant -> constant.name().startsWith(MODULE_CONSTANT_PREFIX)).forEach(constant -> backendValues.add(constant.value()));
        frontendConstantScan.constants().stream().filter(constant -> constant.name().startsWith(MODULE_CONSTANT_PREFIX))
                .forEach(constant -> frontendValues.add(constant.value()));
        for (String value : backendValues) {
            if (!frontendValues.contains(value)) {
                items.add(ReportItem.warning(ReportItem.CODE_FE_BE_MIRROR_MISMATCH, FeatureCandidate.NAMESPACE_MODULE + value,
                        "Module feature constant for '" + value + "' exists in the backend but has no frontend MODULE_FEATURE_ mirror."));
            }
        }
        for (String value : frontendValues) {
            if (!backendValues.contains(value)) {
                ModuleDraft draft = moduleDraftsByStem.get(normalize(value));
                String enumerationHint = draft != null && draft.enumeratedByBackend
                        ? " The backend still enumerates the id at runtime through a non-MODULE_FEATURE constant."
                        : "";
                items.add(ReportItem.warning(ReportItem.CODE_FE_BE_MIRROR_MISMATCH, FeatureCandidate.NAMESPACE_MODULE + value,
                        "Module feature constant for '" + value + "' exists only in the frontend." + enumerationHint));
            }
        }
        Set<String> moduleStems = new LinkedHashSet<>();
        constantScan.constants().stream().filter(constant -> constant.name().startsWith(MODULE_CONSTANT_PREFIX))
                .forEach(constant -> moduleStems.add(normalize(constant.value())));
        for (BackendConstantScan.ScannedConstant constant : constantScan.constants()) {
            if (constant.name().endsWith(PROPERTY_CONSTANT_SUFFIX)) {
                String stem = normalize(stripSuffix(constant.name(), PROPERTY_CONSTANT_SUFFIX));
                if (!moduleStems.contains(stem)) {
                    items.add(ReportItem.info(ReportItem.CODE_MODULE_CONSTANT_ASYMMETRY, constant.name(),
                            "Enabled property constant '" + constant.name() + "' has no matching backend MODULE_FEATURE_ constant."));
                }
            }
        }
    }

    /**
     * Emits the module feature candidates from the merged drafts.
     *
     * @return module candidates in draft insertion order; the caller sorts the full candidate list.
     */
    private List<FeatureCandidate> emitModuleCandidates() {
        List<FeatureCandidate> candidates = new ArrayList<>();
        for (ModuleDraft draft : moduleDraftsByStem.values()) {
            Object defaultValue = draft.configKey == null ? null : valueOf(yamlScan.preferredOccurrence(draft.configKey));
            String backendConstant = draft.backendModuleConstant != null ? draft.backendModuleConstant.name()
                    : draft.propertyConstant != null ? draft.propertyConstant.name() : null;
            candidates.add(new FeatureCandidate(draft.candidateId(), FeatureCandidate.KIND_MODULE_FEATURE, draft.texts == null ? null : draft.texts.name(),
                    moduleDescription(draft), null, draft.configKey, defaultValue, backendConstant,
                    draft.frontendConstant == null ? null : draft.frontendConstant.name(), draft.ownCondition == null ? null : draft.ownCondition.className(), null,
                    draft.enumeratedByBackend, draft.displayedOnAdminPage, draft.documentationUrl));
        }
        return candidates;
    }

    /**
     * Chooses the module candidate description: Artemis i18n first, then constant javadoc, then condition javadoc.
     *
     * @param draft module draft.
     * @return description text, or null.
     */
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

    /**
     * Emits relation candidates for composite conditions reading more than one property key.
     *
     * @param conditionPropertyKeys resolved condition property keys.
     * @return relation candidates sorted by id.
     */
    private List<RelationCandidate> emitRelationCandidates(Map<String, Set<String>> conditionPropertyKeys) {
        List<RelationCandidate> relations = new ArrayList<>();
        for (ConditionClassScan.ScannedCondition condition : conditionScan.conditions()) {
            Set<String> keys = conditionPropertyKeys.getOrDefault(condition.className(), Set.of());
            if (keys.size() < 2) {
                continue;
            }
            List<String> memberIds = new ArrayList<>();
            for (String key : keys) {
                ModuleDraft member = findDraftByConfigKey(key);
                if (member != null && !memberIds.contains(member.candidateId())) {
                    memberIds.add(member.candidateId());
                }
            }
            if (memberIds.size() < 2) {
                continue;
            }
            memberIds.sort(Comparator.naturalOrder());
            ModuleDraft ownDraft = moduleDraftsByStem.get(normalize(stripSuffix(condition.className(), CONDITION_CLASS_SUFFIX)));
            String sourceId = ownDraft == null ? null : ownDraft.candidateId();
            boolean directed = sourceId != null;
            List<String> requiredIds = memberIds.stream().filter(memberId -> !memberId.equals(sourceId)).toList();
            String detail = directed
                    ? "Condition " + condition.className() + " enables " + sourceId + " only when " + String.join(", ", requiredIds) + " is enabled as well."
                    : "Composite condition " + condition.className() + " requires all of " + String.join(", ", requiredIds) + "; the direction is a curation decision.";
            String relationId = RELATION_ID_PREFIX + condition.className();
            relations.add(new RelationCandidate(relationId, RelationCandidate.TYPE_REQUIRES, sourceId, memberIds, directed, condition.className(),
                    RelationCandidate.STATUS_CANDIDATE, detail));
            addEvidence(relationId, EvidenceItem.KIND_CONDITION_CLASS, condition.file(), condition.line(), condition.className(), null);
        }
        relations.sort(Comparator.comparing(RelationCandidate::id));
        return List.copyOf(relations);
    }

    /**
     * Emits runtime toggle candidates from the backend and frontend toggle enums, including mirror diagnostics and
     * usage evidence.
     *
     * @return toggle candidates in enum order.
     */
    private List<FeatureCandidate> emitToggleCandidates() {
        Map<String, BackendFeatureEnumScan.ScannedEnumMember> backendMembers = new LinkedHashMap<>();
        backendToggleScan.members().forEach(member -> backendMembers.putIfAbsent(member.name(), member));
        Map<String, FrontendToggleEnumScan.ScannedToggleMember> frontendMembers = new LinkedHashMap<>();
        frontendToggleScan.members().forEach(member -> frontendMembers.putIfAbsent(member.name(), member));

        Set<String> toggleNames = new LinkedHashSet<>();
        toggleNames.addAll(backendMembers.keySet());
        toggleNames.addAll(frontendMembers.keySet());

        Map<String, String> toggleDocumentationLinks = new LinkedHashMap<>();
        for (AdminPageScan.DocumentationEntry entry : adminPageScan.documentationEntries()) {
            if (entry.identifier().startsWith(TOGGLE_DOC_IDENTIFIER_PREFIX)) {
                String toggleName = entry.identifier().substring(TOGGLE_DOC_IDENTIFIER_PREFIX.length());
                if (toggleNames.contains(toggleName)) {
                    toggleDocumentationLinks.putIfAbsent(toggleName, entry.url());
                    addEvidence(FeatureCandidate.NAMESPACE_TOGGLE + toggleName, EvidenceItem.KIND_ADMIN_PAGE, adminPageScan.file(), entry.line(), entry.identifier(),
                            entry.url());
                }
            }
        }
        List<FeatureCandidate> candidates = new ArrayList<>();
        for (String name : toggleNames) {
            String candidateId = FeatureCandidate.NAMESPACE_TOGGLE + name;
            BackendFeatureEnumScan.ScannedEnumMember backendMember = backendMembers.get(name);
            FrontendToggleEnumScan.ScannedToggleMember frontendMember = frontendMembers.get(name);
            if (backendMember != null) {
                addEvidence(candidateId, EvidenceItem.KIND_BACKEND_ENUM, backendToggleScan.file(), backendMember.line(), name, null);
            }
            else {
                items.add(ReportItem.error(ReportItem.CODE_FE_BE_MIRROR_MISMATCH, candidateId,
                        "Runtime toggle '" + name + "' exists in the frontend FeatureToggle enum but not in the backend Feature enum."));
            }
            if (frontendMember != null) {
                addEvidence(candidateId, EvidenceItem.KIND_FRONTEND_ENUM, frontendToggleScan.file(), frontendMember.line(), name, null);
            }
            else {
                items.add(ReportItem.error(ReportItem.CODE_FE_BE_MIRROR_MISMATCH, candidateId,
                        "Runtime toggle '" + name + "' exists in the backend Feature enum but not in the frontend FeatureToggle enum."));
            }
            FeatureI18nScan.FeatureTexts texts = i18nScan.toggleTexts().get(name);
            if (texts != null) {
                addEvidence(candidateId, EvidenceItem.KIND_I18N, i18nScan.file(), null, "artemisApp.features.toggles." + name, null);
            }
            candidates.add(new FeatureCandidate(candidateId, FeatureCandidate.KIND_RUNTIME_TOGGLE, texts == null ? null : texts.name(),
                    texts == null ? null : texts.description(), texts == null ? null : texts.disableWarning(), null, null,
                    backendMember == null ? null : "Feature." + name, frontendMember == null ? null : "FeatureToggle." + name, null, null, null,
                    frontendMember != null, toggleDocumentationLinks.get(name)));
        }
        for (UsageEvidenceScan.UsageSite site : usageScan.featureToggleSites()) {
            if (toggleNames.contains(site.symbol())) {
                addEvidence(FeatureCandidate.NAMESPACE_TOGGLE + site.symbol(), EvidenceItem.KIND_USAGE_FEATURE_TOGGLE, site.file(), site.line(),
                        "Feature." + site.symbol(), null);
            }
        }
        for (UsageEvidenceScan.UsageSite site : usageScan.templateToggleSites()) {
            if (toggleNames.contains(site.symbol())) {
                addEvidence(FeatureCandidate.NAMESPACE_TOGGLE + site.symbol(), EvidenceItem.KIND_USAGE_TEMPLATE, site.file(), site.line(),
                        "FeatureToggle." + site.symbol(), null);
            }
        }
        return candidates;
    }

    /**
     * Emits Spring profile candidates from backend and frontend profile constants with YAML, i18n, admin page, and
     * compose evidence.
     *
     * @param source Artemis source repository, used for profile YAML existence checks.
     * @return profile candidates in constant order.
     */
    private List<FeatureCandidate> emitProfileCandidates(ArtemisSourceRepository source) {
        Map<String, BackendConstantScan.ScannedConstant> backendProfiles = new LinkedHashMap<>();
        constantScan.constants().stream().filter(constant -> constant.name().startsWith(PROFILE_CONSTANT_PREFIX))
                .forEach(constant -> backendProfiles.putIfAbsent(constant.value(), constant));
        Map<String, FrontendConstantScan.ScannedFrontendConstant> frontendProfiles = new LinkedHashMap<>();
        frontendConstantScan.constants().stream().filter(constant -> constant.name().startsWith(PROFILE_CONSTANT_PREFIX))
                .forEach(constant -> frontendProfiles.putIfAbsent(constant.value(), constant));

        Map<String, String> frontendValuesByName = new LinkedHashMap<>();
        frontendConstantScan.constants().forEach(constant -> frontendValuesByName.putIfAbsent(constant.name(), constant.value()));
        Set<String> displayedProfiles = new LinkedHashSet<>();
        Map<String, AdminPageScan.MembershipEntry> displayedProfileEntries = new LinkedHashMap<>();
        for (AdminPageScan.MembershipEntry entry : adminPageScan.displayedProfiles()) {
            String value = frontendValuesByName.get(entry.identifier());
            if (value != null) {
                displayedProfiles.add(value);
                displayedProfileEntries.putIfAbsent(value, entry);
            }
        }
        Map<String, AdminPageScan.DocumentationEntry> profileDocumentation = new LinkedHashMap<>();
        for (AdminPageScan.DocumentationEntry entry : adminPageScan.documentationEntries()) {
            if (entry.identifier().startsWith(PROFILE_CONSTANT_PREFIX)) {
                String value = frontendValuesByName.get(entry.identifier());
                if (value != null) {
                    profileDocumentation.putIfAbsent(value, entry);
                }
            }
        }

        Set<String> profileIds = new LinkedHashSet<>();
        profileIds.addAll(backendProfiles.keySet());
        profileIds.addAll(frontendProfiles.keySet());
        List<FeatureCandidate> candidates = new ArrayList<>();
        for (String profileId : profileIds) {
            String candidateId = FeatureCandidate.NAMESPACE_PROFILE + profileId;
            BackendConstantScan.ScannedConstant backendConstant = backendProfiles.get(profileId);
            FrontendConstantScan.ScannedFrontendConstant frontendConstant = frontendProfiles.get(profileId);
            if (backendConstant != null) {
                addEvidence(candidateId, EvidenceItem.KIND_BACKEND_CONSTANT, constantScan.file(), backendConstant.line(), backendConstant.name(), null);
            }
            if (frontendConstant != null) {
                addEvidence(candidateId, EvidenceItem.KIND_FRONTEND_CONSTANT, frontendConstantScan.file(), frontendConstant.line(), frontendConstant.name(), null);
            }
            String profileYaml = ArtemisSourceConventions.Files.profileConfiguration(profileId);
            if (source.fileExists(profileYaml)) {
                addEvidence(candidateId, EvidenceItem.KIND_PROFILE_YAML, profileYaml, null, profileId, null);
            }
            if ("jenkins".equals(profileId) && composeScan.jenkinsComposeFile() != null) {
                addEvidence(candidateId, EvidenceItem.KIND_COMPOSE_FILE, composeScan.jenkinsComposeFile(), null, profileId, null);
            }
            FeatureI18nScan.FeatureTexts texts = i18nScan.profileTexts().get(profileId);
            if (texts != null) {
                addEvidence(candidateId, EvidenceItem.KIND_I18N, i18nScan.file(), null, "artemisApp.features.profiles." + profileId, null);
            }
            AdminPageScan.MembershipEntry membershipEntry = displayedProfileEntries.get(profileId);
            if (membershipEntry != null) {
                addEvidence(candidateId, EvidenceItem.KIND_ADMIN_PAGE, adminPageScan.file(), membershipEntry.line(), membershipEntry.identifier(), "display membership");
            }
            AdminPageScan.DocumentationEntry documentationEntry = profileDocumentation.get(profileId);
            candidates.add(new FeatureCandidate(candidateId, FeatureCandidate.KIND_SPRING_PROFILE, texts == null ? null : texts.name(),
                    texts == null ? null : texts.description(), null, null, null, backendConstant == null ? null : backendConstant.name(),
                    frontendConstant == null ? null : frontendConstant.name(), null, profileId, null, displayedProfiles.contains(profileId),
                    documentationEntry == null ? null : documentationEntry.url()));
        }
        return candidates;
    }

    /**
     * Emits infrastructure candidates for the paired database compose alternatives.
     *
     * @return infrastructure candidates, at most one per database token.
     */
    private List<FeatureCandidate> emitInfrastructureCandidates() {
        Map<String, List<ComposeFileScan.ComposeAlternative>> alternativesByDatabase = new TreeMap<>();
        for (ComposeFileScan.ComposeAlternative alternative : composeScan.alternatives()) {
            String databaseId = ComposeFileScan.MYSQL_TOKEN.equals(alternative.databaseToken()) ? "mysql" : "postgres";
            alternativesByDatabase.computeIfAbsent(databaseId, unused -> new ArrayList<>()).add(alternative);
        }
        List<FeatureCandidate> candidates = new ArrayList<>();
        alternativesByDatabase.forEach((databaseId, alternatives) -> {
            String candidateId = FeatureCandidate.NAMESPACE_INFRASTRUCTURE + databaseId;
            for (ComposeFileScan.ComposeAlternative alternative : alternatives) {
                String detail = alternative.pairedFile() == null ? "no paired alternative found" : "paired with " + alternative.pairedFile();
                addEvidence(candidateId, EvidenceItem.KIND_COMPOSE_FILE, alternative.file(), null, databaseId, detail);
            }
            candidates.add(new FeatureCandidate(candidateId, FeatureCandidate.KIND_INFRASTRUCTURE, null,
                    "Database alternative encoded by paired Docker compose stacks.", null, null, null, null, null, null, null, null, null, null));
        });
        return candidates;
    }

    /**
     * Emits config key candidates for every enabled property constant.
     *
     * @return config key candidates in declaration order.
     */
    private List<FeatureCandidate> emitConfigKeyCandidates() {
        List<FeatureCandidate> candidates = new ArrayList<>();
        for (BackendConstantScan.ScannedConstant constant : constantScan.constants()) {
            if (!constant.name().endsWith(PROPERTY_CONSTANT_SUFFIX)) {
                continue;
            }
            String candidateId = FeatureCandidate.NAMESPACE_CONFIG_KEY + constant.value();
            addEvidence(candidateId, EvidenceItem.KIND_BACKEND_CONSTANT, constantScan.file(), constant.line(), constant.name(), null);
            YamlConfigScan.KeyOccurrence occurrence = yamlScan.preferredOccurrence(constant.value());
            if (occurrence != null) {
                addEvidence(candidateId, EvidenceItem.KIND_YAML_DEFAULT, occurrence.file(), occurrence.line(), constant.value(), String.valueOf(occurrence.value()));
            }
            candidates.add(new FeatureCandidate(candidateId, FeatureCandidate.KIND_CONFIG_KEY, null, constant.javadoc(), null, constant.value(), valueOf(occurrence),
                    constant.name(), null, null, null, null, null, null));
        }
        return candidates;
    }

    /**
     * Attaches {@code @Conditional} usage sites as evidence to the modules guarded by the referenced condition class,
     * fanning composite conditions out to every member module.
     *
     * @param conditionPropertyKeys resolved condition property keys.
     */
    private void attachConditionalUsageEvidence(Map<String, Set<String>> conditionPropertyKeys) {
        Map<String, List<String>> memberCandidateIdsByCondition = new LinkedHashMap<>();
        for (ConditionClassScan.ScannedCondition condition : conditionScan.conditions()) {
            List<String> memberIds = new ArrayList<>();
            ModuleDraft ownDraft = moduleDraftsByStem.get(normalize(stripSuffix(condition.className(), CONDITION_CLASS_SUFFIX)));
            if (ownDraft != null) {
                memberIds.add(ownDraft.candidateId());
            }
            else {
                for (String key : conditionPropertyKeys.getOrDefault(condition.className(), Set.of())) {
                    ModuleDraft member = findDraftByConfigKey(key);
                    if (member != null && !memberIds.contains(member.candidateId())) {
                        memberIds.add(member.candidateId());
                    }
                }
            }
            memberCandidateIdsByCondition.put(condition.className(), memberIds);
        }
        for (UsageEvidenceScan.UsageSite site : usageScan.conditionalSites()) {
            for (String candidateId : memberCandidateIdsByCondition.getOrDefault(site.symbol(), List.of())) {
                String detail = memberCandidateIdsByCondition.get(site.symbol()).size() > 1 ? "via composite condition " + site.symbol() : null;
                addEvidence(candidateId, EvidenceItem.KIND_USAGE_CONDITIONAL, site.file(), site.line(), site.symbol(), detail);
            }
        }
    }

    /**
     * Returns the draft registered for a value, creating it with the given display id when absent. An existing draft
     * keeps its display id unless the new one comes from an authoritative constant value containing a separator.
     *
     * @param value anchor value or stem the draft is keyed by.
     * @param displayId display id to use when the draft is created.
     * @return module draft.
     */
    private ModuleDraft draftFor(String value, String displayId) {
        ModuleDraft draft = moduleDraftsByStem.computeIfAbsent(normalize(value), unused -> new ModuleDraft());
        if (draft.displayId == null || (!draft.displayId.equals(displayId) && displayId.contains("-"))) {
            draft.displayId = displayId;
        }
        return draft;
    }

    /**
     * Finds the module draft owning a config key.
     *
     * @param configKey dotted configuration key.
     * @return owning draft, or null.
     */
    private ModuleDraft findDraftByConfigKey(String configKey) {
        for (ModuleDraft draft : moduleDraftsByStem.values()) {
            if (configKey.equals(draft.configKey)) {
                return draft;
            }
        }
        return null;
    }

    /**
     * Finds a scanned backend constant by name.
     *
     * @param name constant name.
     * @return scanned constant, or empty.
     */
    private Optional<BackendConstantScan.ScannedConstant> findConstant(String name) {
        return constantScan.constants().stream().filter(constant -> constant.name().equals(name)).findFirst();
    }

    /**
     * Records one evidence item.
     *
     * @param candidateId candidate or relation candidate id.
     * @param kind evidence kind.
     * @param file checkout-relative path.
     * @param line 1-based line, or null.
     * @param symbol observed symbol, or null.
     * @param detail human-readable detail, or null.
     */
    private void addEvidence(String candidateId, String kind, String file, Integer line, String symbol, String detail) {
        evidence.add(new EvidenceItem(candidateId, kind, file, line, symbol, detail));
    }

    /**
     * Extracts the scalar value of an occurrence.
     *
     * @param occurrence key occurrence, or null.
     * @return scalar value, or null.
     */
    private Object valueOf(YamlConfigScan.KeyOccurrence occurrence) {
        return occurrence == null ? null : occurrence.value();
    }

    /**
     * Normalizes an anchor symbol or value for joining: lower case with separators removed, so
     * {@code TUTORIAL_GROUP}, {@code TutorialGroup}, and {@code tutorialgroup} join onto the same draft.
     *
     * @param value symbol or value.
     * @return normalized join key.
     */
    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    /**
     * Strips a suffix from a symbol when present.
     *
     * @param value symbol.
     * @param suffix suffix to strip.
     * @return symbol without the suffix.
     */
    private static String stripSuffix(String value, String suffix) {
        return value.endsWith(suffix) ? value.substring(0, value.length() - suffix.length()) : value;
    }
}
