package de.tum.cit.aet.artemis.featuremodel.extraction.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMappingSource;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ArtifactMappingTargets;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ConfigDerivationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ConfigDerivationReport.ConfigKeyResolution;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ConfigDerivationReport.MemberConfigDerivation;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigInjection;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigurationDefault;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigurationDefaults;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConfigurationEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.MappingHint;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;

/**
 * Derives the artifact mappings and required capabilities of every member from the persisted scan facts and applies
 * the precedence manifest over derivation. A technical member first receives its structural mapping — the compose
 * stack of an {@code infra:} anchor or the Spring profile tokens of a {@code profile:} anchor — and then, like a
 * functional member, its environment mappings. Candidate keys come from three structural sources: {@code @Value} keys
 * whose injection sites are all guarded by the member's own guard, YAML keys under {@code @ConfigurationProperties}
 * prefixes declared in such guarded files, and YAML keys under the namespace of the member's enabled key. The guard is
 * the condition class of a functional member or the {@code @Profile} token of a technical one. A key that belongs to
 * another candidate's enabled-key namespace is never attributed to the member, and a key injected anywhere without the
 * member's guard is not attributable either, so cross-cutting core keys such as the server URL stay out of feature
 * mappings. Candidates classify as deployment inputs or listed tunables through {@link ConfigKeyClassifier}. The
 * emitted order is deterministic: manifest entries in declaration order, then derived non-secret keys sorted, then
 * derived secret keys sorted. The required capabilities of a functional member follow its emitted deployment inputs:
 * {@code <id>-service} when at least one input is not a secret, {@code <id>-secret} when at least one is.
 */
class ConfigMappingDeriver {

    private static final String ENABLED_KEY_SUFFIX = ".enabled";

    private static final String CAPABILITY_SERVICE_SUFFIX = "-service";

    private static final String CAPABILITY_SECRET_SUFFIX = "-secret";

    private static final String COMPOSE_TARGET = "docker-compose.override.yml";

    private static final String COMPOSE_PATH_SUFFIX = ".composeFile";

    private static final String COMPOSE_FILE_SUFFIX = ".yml";

    private static final String ENV_TARGET = ".env";

    private static final String SPRING_PROFILES_ACTIVE = "SPRING_PROFILES_ACTIVE";

    /**
     * Derivation result.
     *
     * @param resolvedFeatures resolved member semantics carrying the derived mappings and capabilities, in the input
     *            order.
     * @param derivation per-member key resolutions for {@code config-derivation.json} and the report section.
     * @param items derivation diagnostics.
     */
    record Result(List<ResolvedFeatureScope> resolvedFeatures, ConfigDerivationReport derivation, List<ReportItem> items) {
    }

    /** One derived candidate key with its classification and evidence. */
    private record DerivedCandidate(String key, boolean deploymentInput, boolean secret, List<EvidenceItem> evidence) {
    }

    /** One key resolved for emission, carrying the report resolution and the mapping secret flag. */
    private record EmittedKey(ConfigKeyResolution resolution, Boolean mappingSecret) {
    }

    /**
     * Guard that attributes a file to a member.
     *
     * @param label human-readable guard used in evidence details.
     * @param matches whether an injection file carries the guard.
     */
    private record MemberGuard(String label, Predicate<ExtractedConfigInjection> matches) {
    }

    /** Precedence-merge outcome of one member. */
    private record MemberResolution(List<MappingHint> mappings, List<ConfigKeyResolution> keys, List<String> requiresCapabilities) {
    }

    /**
     * Derives and merges the mappings and capabilities of every member.
     *
     * @param includedFeatures resolved member semantics of the curation step.
     * @param configInjections guarded injection sites of the consumed scan.
     * @param configDefaults scanned YAML defaults of the consumed scan.
     * @param candidates extracted candidates providing guards, profiles, and enabled keys.
     * @param evidence evidence items of the consumed scan, supplying the compose files of infrastructure anchors.
     * @return updated member semantics, the derivation report, and diagnostics.
     */
    Result derive(List<ResolvedFeatureScope> includedFeatures, List<ExtractedConfigInjection> configInjections, ExtractedConfigurationDefaults configDefaults,
            List<FeatureCandidate> candidates, List<EvidenceItem> evidence) {
        Map<String, FeatureCandidate> candidatesById = new LinkedHashMap<>();
        candidates.forEach(candidate -> candidatesById.putIfAbsent(candidate.id(), candidate));
        Map<String, String> namespacesByCandidate = enabledKeyNamespaces(candidates);
        Map<String, List<ExtractedConfigInjection>> injectionSitesByKey = injectionSitesByKey(configInjections);
        Map<String, List<EvidenceItem>> evidenceByCandidate = new LinkedHashMap<>();
        evidence.forEach(item -> evidenceByCandidate.computeIfAbsent(item.candidateId(), unused -> new ArrayList<>()).add(item));

        List<ReportItem> items = new ArrayList<>();
        List<ResolvedFeatureScope> resolvedFeatures = new ArrayList<>();
        Map<String, MemberConfigDerivation> membersById = new TreeMap<>();
        for (ResolvedFeatureScope member : includedFeatures) {
            FeatureCandidate candidate = candidatesById.get(member.candidateId());
            boolean technical = CurationReport.SOURCE_TECHNICAL.equals(member.membershipSource());
            List<MappingHint> mappings = new ArrayList<>();
            if (technical) {
                mappings.addAll(structuralMappings(member, candidate, evidenceByCandidate.getOrDefault(member.candidateId(), List.of()), items));
            }
            Map<String, DerivedCandidate> derived = deriveCandidates(candidate, guardOf(candidate, technical), configInjections, injectionSitesByKey,
                    configDefaults, namespacesByCandidate);
            MemberResolution resolution = mergeByPrecedence(member, derived, items);
            mappings.addAll(resolution.mappings());
            resolvedFeatures.add(member.withDerivation(List.copyOf(mappings), technical ? List.of() : resolution.requiresCapabilities()));
            membersById.put(member.id(), new MemberConfigDerivation(member.id(), member.candidateId(), resolution.keys()));
        }
        return new Result(List.copyOf(resolvedFeatures), new ConfigDerivationReport(List.copyOf(membersById.values())), List.copyOf(items));
    }

    /**
     * Derives the structural mapping of a technical member: the compose stack selected by an infrastructure anchor,
     * written as {@code <group>.composeFile}, or the Spring profile tokens activated by a profile anchor. Other anchor
     * kinds have no structural mapping.
     *
     * @param member resolved technical member.
     * @param candidate extracted candidate of the member, or null.
     * @param evidence evidence items of the candidate.
     * @param items diagnostics sink.
     * @return structural mapping, or empty when the anchor kind has none or it cannot be derived.
     */
    private List<MappingHint> structuralMappings(ResolvedFeatureScope member, FeatureCandidate candidate, List<EvidenceItem> evidence, List<ReportItem> items) {
        if (candidate == null) {
            return List.of();
        }
        if (FeatureCandidate.KIND_INFRASTRUCTURE.equals(candidate.kind())) {
            String composeFile = baseComposeFile(evidence);
            if (composeFile == null) {
                items.add(ReportItem.error(ReportItem.CODE_TECHNICAL_MAPPING_UNDERIVABLE, member.id(), "Infrastructure anchor '" + candidate.id()
                        + "' has no base compose file docker/<token>.yml among its compose evidence, so the compose mapping of '" + member.id()
                        + "' cannot be derived."));
                return List.of();
            }
            String placement = member.group() != null ? member.group() : member.parent();
            return List.of(new MappingHint(COMPOSE_TARGET, placement + COMPOSE_PATH_SUFFIX, ArtifactMappingSource.SELECTION, composeFile, null, null));
        }
        if (FeatureCandidate.KIND_SPRING_PROFILE.equals(candidate.kind())) {
            if (candidate.springProfile() == null) {
                items.add(ReportItem.error(ReportItem.CODE_TECHNICAL_MAPPING_UNDERIVABLE, member.id(), "Profile anchor '" + candidate.id()
                        + "' carries no Spring profile name, so the profile mapping of '" + member.id() + "' cannot be derived."));
                return List.of();
            }
            String tokens = member.profiles().isEmpty() ? candidate.springProfile() : String.join(",", member.profiles());
            return List.of(new MappingHint(ENV_TARGET, SPRING_PROFILES_ACTIVE, ArtifactMappingSource.SELECTION, tokens, null, null));
        }
        return List.of();
    }

    /**
     * Finds the base compose stack of an infrastructure anchor: the compose-file evidence whose file name is the bare
     * database token, for example {@code docker/mysql.yml} among the paired {@code *-mysql.yml} stacks.
     *
     * @param evidence evidence items of the candidate.
     * @return checkout-relative base compose file, or null when none exists.
     */
    private String baseComposeFile(List<EvidenceItem> evidence) {
        return evidence.stream().filter(item -> EvidenceItem.KIND_COMPOSE_FILE.equals(item.kind()) && item.symbol() != null)
                .filter(item -> item.file().endsWith("/" + item.symbol() + COMPOSE_FILE_SUFFIX)).map(EvidenceItem::file).sorted().findFirst().orElse(null);
    }

    /**
     * Resolves the guard that attributes injection files to a member: the {@code @Conditional} condition class of a
     * functional member, or the {@code @Profile} constant and profile name of a technical profile member.
     *
     * @param candidate extracted candidate of the member, or null.
     * @param technical whether the member is a technical member.
     * @return guard, or null when the candidate has none.
     */
    private MemberGuard guardOf(FeatureCandidate candidate, boolean technical) {
        if (candidate == null) {
            return null;
        }
        if (technical) {
            if (candidate.springProfile() == null) {
                return null;
            }
            Set<String> tokens = new LinkedHashSet<>();
            if (candidate.serverConstant() != null) {
                tokens.add(candidate.serverConstant());
            }
            tokens.add(candidate.springProfile());
            String label = "@Profile(" + tokens.iterator().next() + ")";
            return new MemberGuard(label, injection -> injection.profileGuards().stream().anyMatch(tokens::contains));
        }
        String conditionClass = candidate.serverConditionClass();
        if (conditionClass == null) {
            return null;
        }
        return new MemberGuard("@Conditional(" + conditionClass + ")", injection -> injection.conditionGuards().contains(conditionClass));
    }

    /**
     * Applies the per-key precedence for one member: manifest include entries confirm or add keys in declaration
     * order, exclude entries reject derived keys, and the remaining derived deployment inputs emit non-secret keys
     * before secret keys, each sorted. Tunables are listed as skipped. The required capabilities follow the emitted
     * keys.
     *
     * @param member resolved member semantics.
     * @param derived derived candidate keys of the member.
     * @param items diagnostics sink.
     * @return resolved mappings, key resolutions, and capabilities.
     */
    private MemberResolution mergeByPrecedence(ResolvedFeatureScope member, Map<String, DerivedCandidate> derived, List<ReportItem> items) {
        Map<String, EmittedKey> emitted = new LinkedHashMap<>();
        List<ConfigKeyResolution> rejected = new ArrayList<>();
        for (ConfigurationEntry entry : member.configuration()) {
            applyManifestEntry(member, entry, derived, emitted, rejected, items);
        }
        appendDerivedInputs(member, derived, emitted, rejected, items);

        List<MappingHint> mappings = new ArrayList<>();
        List<ConfigKeyResolution> keys = new ArrayList<>();
        boolean anyService = false;
        boolean anySecret = false;
        for (EmittedKey key : emitted.values()) {
            mappings.add(new MappingHint(ArtifactMappingTargets.OVERLAY_TARGET, key.resolution().key(), ArtifactMappingSource.ENVIRONMENT, null, null,
                    key.mappingSecret()));
            keys.add(key.resolution());
            anyService |= !key.resolution().secret();
            anySecret |= key.resolution().secret();
        }
        keys.addAll(skippedTunables(member, derived, emitted, rejected, items));
        keys.addAll(rejected);
        List<String> capabilities = new ArrayList<>();
        if (anyService) {
            capabilities.add(member.id() + CAPABILITY_SERVICE_SUFFIX);
        }
        if (anySecret) {
            capabilities.add(member.id() + CAPABILITY_SECRET_SUFFIX);
        }
        return new MemberResolution(List.copyOf(mappings), List.copyOf(keys), List.copyOf(capabilities));
    }

    /**
     * Applies one manifest configuration entry. Include entries confirm or add the key at their declaration position;
     * exclude entries reject the key.
     *
     * @param member resolved member semantics.
     * @param entry manifest configuration entry.
     * @param derived derived candidate keys of the member.
     * @param emitted emitted keys, updated in place.
     * @param rejected rejected key resolutions, updated in place.
     * @param items diagnostics sink.
     */
    private void applyManifestEntry(ResolvedFeatureScope member, ConfigurationEntry entry, Map<String, DerivedCandidate> derived,
            Map<String, EmittedKey> emitted, List<ConfigKeyResolution> rejected, List<ReportItem> items) {
        String entryLabel = "configuration entry of '" + member.id() + "' for key '" + entry.key() + "'";
        DerivedCandidate candidate = derived.get(entry.key());
        if (FeatureScopeManifest.CONFIGURATION_ACTION_EXCLUDE.equals(entry.action())) {
            String detail = candidate == null ? "derivation did not produce the key" : candidate.deploymentInput() ? "rejects the derived deployment input"
                    : "rejects a key derivation classified as a tunable";
            rejected.add(new ConfigKeyResolution(entry.key(), ConfigDerivationReport.ORIGIN_MANIFEST, ConfigDerivationReport.DECISION_REJECTED,
                    candidate != null && candidate.secret(), candidate == null ? List.of() : candidate.evidence(), detail));
            items.add(ReportItem.info(ReportItem.CODE_CONFIG_MAPPING_REJECTED, member.id(),
                    entryLabel + " rejects the key; no mapping is emitted" + (candidate == null ? ", and derivation did not produce it" : "") + "."));
            return;
        }
        boolean secret = entry.secret() != null ? entry.secret() : candidate != null && candidate.deploymentInput() && candidate.secret();
        Boolean mappingSecret = entry.secret() != null ? entry.secret() : secret ? Boolean.TRUE : null;
        String detail = candidate == null ? null : candidate.deploymentInput() ? "confirms the derived deployment input"
                : "overrides the tunable classification of the derived candidate";
        emitted.putIfAbsent(entry.key(), new EmittedKey(new ConfigKeyResolution(entry.key(), ConfigDerivationReport.ORIGIN_MANIFEST,
                ConfigDerivationReport.DECISION_CONFIRMED, secret, candidate == null ? List.of() : candidate.evidence(), detail), mappingSecret));
    }

    /**
     * Appends the derived deployment inputs nobody declared or rejected: non-secret keys sorted by key, then secret
     * keys sorted by key.
     *
     * @param member resolved member semantics.
     * @param derived derived candidate keys of the member.
     * @param emitted emitted keys, updated in place.
     * @param rejected rejected key resolutions.
     * @param items diagnostics sink.
     */
    private void appendDerivedInputs(ResolvedFeatureScope member, Map<String, DerivedCandidate> derived, Map<String, EmittedKey> emitted,
            List<ConfigKeyResolution> rejected, List<ReportItem> items) {
        List<DerivedCandidate> inputs = derived.values().stream()
                .filter(DerivedCandidate::deploymentInput)
                .filter(candidate -> !emitted.containsKey(candidate.key()))
                .filter(candidate -> rejected.stream().noneMatch(resolution -> resolution.key().equals(candidate.key())))
                .sorted(Comparator.comparing(DerivedCandidate::secret).thenComparing(DerivedCandidate::key))
                .toList();
        for (DerivedCandidate input : inputs) {
            emitted.put(input.key(), new EmittedKey(new ConfigKeyResolution(input.key(), ConfigDerivationReport.ORIGIN_DERIVED,
                    ConfigDerivationReport.DECISION_DERIVED, input.secret(), input.evidence(), null), input.secret() ? Boolean.TRUE : null));
            items.add(ReportItem.info(ReportItem.CODE_CONFIG_MAPPING_DERIVED, member.id(), "Derived " + (input.secret() ? "secret " : "")
                    + "deployment input '" + input.key() + "' from guarded Artemis structure."));
        }
    }

    /**
     * Lists the derived candidates that stay tunables, sorted by key.
     *
     * @param member resolved member semantics.
     * @param derived derived candidate keys of the member.
     * @param emitted emitted keys.
     * @param rejected rejected key resolutions.
     * @param items diagnostics sink.
     * @return skipped-tunable resolutions.
     */
    private List<ConfigKeyResolution> skippedTunables(ResolvedFeatureScope member, Map<String, DerivedCandidate> derived, Map<String, EmittedKey> emitted,
            List<ConfigKeyResolution> rejected, List<ReportItem> items) {
        List<ConfigKeyResolution> skipped = new ArrayList<>();
        List<DerivedCandidate> tunables = derived.values().stream()
                .filter(candidate -> !candidate.deploymentInput())
                .filter(candidate -> !emitted.containsKey(candidate.key()))
                .filter(candidate -> rejected.stream().noneMatch(resolution -> resolution.key().equals(candidate.key())))
                .sorted(Comparator.comparing(DerivedCandidate::key))
                .toList();
        for (DerivedCandidate tunable : tunables) {
            skipped.add(new ConfigKeyResolution(tunable.key(), ConfigDerivationReport.ORIGIN_DERIVED, ConfigDerivationReport.DECISION_SKIPPED,
                    tunable.secret(), tunable.evidence(), null));
            items.add(ReportItem.info(ReportItem.CODE_CONFIG_MAPPING_TUNABLE_SKIPPED, member.id(),
                    "Candidate key '" + tunable.key() + "' stays a tunable and is not emitted."));
        }
        return skipped;
    }

    /**
     * Collects and classifies the derived candidate keys of one member from the three structural sources. The
     * member's own enabled key is excluded because it already backs the auto-derived toggle mapping, and any key
     * owned by another candidate's enabled-key namespace is excluded because it belongs to that candidate.
     *
     * @param candidate extracted candidate of the member, or null.
     * @param guard guard attributing injection files to the member, or null when the candidate has none.
     * @param configInjections guarded injection sites of the scan.
     * @param injectionSitesByKey all injection sites per key.
     * @param configDefaults scanned YAML defaults.
     * @param namespacesByCandidate enabled-key namespaces per candidate id.
     * @return derived candidates keyed and sorted by key.
     */
    private Map<String, DerivedCandidate> deriveCandidates(FeatureCandidate candidate, MemberGuard guard, List<ExtractedConfigInjection> configInjections,
            Map<String, List<ExtractedConfigInjection>> injectionSitesByKey, ExtractedConfigurationDefaults configDefaults,
            Map<String, String> namespacesByCandidate) {
        if (candidate == null) {
            return Map.of();
        }
        Map<String, List<EvidenceItem>> evidenceByKey = new TreeMap<>();
        if (guard != null) {
            collectGuardedFileKeys(candidate, guard, configInjections, injectionSitesByKey, configDefaults, evidenceByKey);
        }
        String ownNamespace = namespacesByCandidate.get(candidate.id());
        if (ownNamespace != null) {
            for (String key : configDefaults.occurrencesByKey().keySet()) {
                if (key.startsWith(ownNamespace + ".")) {
                    evidenceByKey.computeIfAbsent(key, unused -> new ArrayList<>());
                }
            }
        }
        if (candidate.configKey() != null) {
            evidenceByKey.remove(candidate.configKey());
        }
        evidenceByKey.keySet().removeIf(key -> ownedByAnotherCandidate(key, ownNamespace, namespacesByCandidate));

        Map<String, DerivedCandidate> derived = new TreeMap<>();
        for (Map.Entry<String, List<EvidenceItem>> entry : evidenceByKey.entrySet()) {
            String key = entry.getKey();
            ExtractedConfigurationDefault occurrence = configDefaults.preferredOccurrence(key);
            List<EvidenceItem> evidence = new ArrayList<>(entry.getValue());
            if (occurrence != null) {
                evidence.add(new EvidenceItem(candidate.id(), EvidenceItem.KIND_USAGE_CONFIG_YAML, occurrence.file(), occurrence.line(), key,
                        "scanned YAML default"));
            }
            Object preferredDefault = occurrence == null ? null : occurrence.value();
            derived.put(key, new DerivedCandidate(key, ConfigKeyClassifier.isDeploymentInput(key, preferredDefault),
                    ConfigKeyClassifier.isSecret(key, preferredDefault), List.copyOf(evidence)));
        }
        return derived;
    }

    /**
     * Collects candidate keys from the files carrying the member's guard: injected keys whose every injection site
     * carries the guard, and YAML keys under the prefixes such files declare.
     *
     * @param candidate extracted candidate of the member.
     * @param guard guard attributing injection files to the member.
     * @param configInjections guarded injection sites of the scan.
     * @param injectionSitesByKey all injection sites per key.
     * @param configDefaults scanned YAML defaults.
     * @param evidenceByKey evidence sink per candidate key, updated in place.
     */
    private void collectGuardedFileKeys(FeatureCandidate candidate, MemberGuard guard, List<ExtractedConfigInjection> configInjections,
            Map<String, List<ExtractedConfigInjection>> injectionSitesByKey, ExtractedConfigurationDefaults configDefaults,
            Map<String, List<EvidenceItem>> evidenceByKey) {
        for (ExtractedConfigInjection injection : configInjections) {
            if (!guard.matches().test(injection)) {
                continue;
            }
            for (String key : injection.valueKeys()) {
                if (everySiteGuardedBy(injectionSitesByKey.get(key), guard)) {
                    evidenceByKey.computeIfAbsent(key, unused -> new ArrayList<>()).add(new EvidenceItem(candidate.id(),
                            EvidenceItem.KIND_USAGE_CONFIG_INJECTION, injection.file(), null, key, "@Value placeholder under " + guard.label()));
                }
            }
            for (ExtractedConfigInjection.ConfigurationPropertiesPrefix prefix : injection.propertyPrefixes()) {
                for (String key : configDefaults.occurrencesByKey().keySet()) {
                    if (key.startsWith(prefix.prefix() + ".")) {
                        evidenceByKey.computeIfAbsent(key, unused -> new ArrayList<>()).add(new EvidenceItem(candidate.id(),
                                EvidenceItem.KIND_USAGE_CONFIG_PREFIX, injection.file(), null, prefix.prefix(),
                                "@ConfigurationProperties prefix declared by " + prefix.declaringType() + " under " + guard.label()));
                    }
                }
            }
        }
    }

    /**
     * Checks whether every injection site of a key carries the member's guard. A key that is also injected in an
     * unguarded file or under another feature's guard is a cross-cutting key and is not attributable to the member.
     *
     * @param sites all files injecting the key.
     * @param guard guard attributing injection files to the member.
     * @return true when the key is attributable to the member.
     */
    private boolean everySiteGuardedBy(List<ExtractedConfigInjection> sites, MemberGuard guard) {
        return sites != null && !sites.isEmpty() && sites.stream().allMatch(guard.matches());
    }

    /**
     * Checks whether a key belongs to another candidate: the longest enabled-key namespace containing the key exists
     * and is not the member's own namespace.
     *
     * @param key candidate key.
     * @param ownNamespace enabled-key namespace of the member, or null.
     * @param namespacesByCandidate enabled-key namespaces per candidate id.
     * @return true when another candidate owns the key.
     */
    private boolean ownedByAnotherCandidate(String key, String ownNamespace, Map<String, String> namespacesByCandidate) {
        String owner = null;
        for (String namespace : namespacesByCandidate.values()) {
            if (key.startsWith(namespace + ".") && (owner == null || namespace.length() > owner.length())) {
                owner = namespace;
            }
        }
        return owner != null && !owner.equals(ownNamespace);
    }

    /**
     * Indexes the enabled-key namespaces of every candidate whose config key ends in {@code .enabled}.
     *
     * @param candidates extracted candidates.
     * @return namespace per candidate id.
     */
    private Map<String, String> enabledKeyNamespaces(List<FeatureCandidate> candidates) {
        Map<String, String> namespaces = new LinkedHashMap<>();
        for (FeatureCandidate candidate : candidates) {
            String configKey = candidate.configKey();
            if (configKey != null && configKey.endsWith(ENABLED_KEY_SUFFIX)) {
                namespaces.putIfAbsent(candidate.id(), configKey.substring(0, configKey.length() - ENABLED_KEY_SUFFIX.length()));
            }
        }
        return namespaces;
    }

    /**
     * Indexes all injection sites per injected key.
     *
     * @param configInjections guarded injection sites of the scan.
     * @return injection files per key.
     */
    private Map<String, List<ExtractedConfigInjection>> injectionSitesByKey(List<ExtractedConfigInjection> configInjections) {
        Map<String, List<ExtractedConfigInjection>> sitesByKey = new LinkedHashMap<>();
        for (ExtractedConfigInjection injection : configInjections) {
            for (String key : injection.valueKeys()) {
                sitesByKey.computeIfAbsent(key, unused -> new ArrayList<>()).add(injection);
            }
        }
        return sitesByKey;
    }
}
