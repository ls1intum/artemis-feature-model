package de.tum.cit.aet.artemis.featuremodel.extraction.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMappingSource;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ArtifactMappingTargets;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ConfigDerivationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ConfigDerivationReport.ConfigKeyResolution;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ConfigDerivationReport.MemberConfigDerivation;
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
 * Derives the environment-facing configuration mappings of every functional member from the persisted scan facts and
 * applies the precedence manifest over derivation. Candidate keys come from three structural sources:
 * {@code @Value} keys whose injection sites are all guarded by the member's own condition class, YAML keys under
 * {@code @ConfigurationProperties} prefixes declared in such guarded files, and YAML keys under the namespace of the
 * member's enabled key. A key that belongs to another candidate's enabled-key namespace is never attributed to the
 * member, and a key injected anywhere without the member's guard is not attributable either, so cross-cutting core
 * keys such as the server URL stay out of feature mappings. Candidates classify as deployment inputs or listed
 * tunables through {@link ConfigKeyClassifier}. The emitted order is deterministic: manifest entries in declaration
 * order, then derived non-secret keys sorted, then derived secret keys sorted. Technical members keep their declared
 * mapping hints untouched.
 */
class ConfigMappingDeriver {

    private static final String ENABLED_KEY_SUFFIX = ".enabled";

    /**
     * Derivation result.
     *
     * @param resolvedFeatures resolved member semantics with the functional members' artifact mappings replaced by
     *            the resolved configuration mappings, in the input order.
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
     * Derives and merges the configuration mappings of every member.
     *
     * @param includedFeatures resolved member semantics of the curation step.
     * @param configInjections guarded injection sites of the consumed scan.
     * @param configDefaults scanned YAML defaults of the consumed scan.
     * @param candidates extracted candidates providing condition classes and enabled keys.
     * @return updated member semantics, the derivation report, and diagnostics.
     */
    Result derive(List<ResolvedFeatureScope> includedFeatures, List<ExtractedConfigInjection> configInjections, ExtractedConfigurationDefaults configDefaults,
            List<FeatureCandidate> candidates) {
        Map<String, FeatureCandidate> candidatesById = new LinkedHashMap<>();
        candidates.forEach(candidate -> candidatesById.putIfAbsent(candidate.id(), candidate));
        Map<String, String> namespacesByCandidate = enabledKeyNamespaces(candidates);
        Map<String, List<ExtractedConfigInjection>> injectionSitesByKey = injectionSitesByKey(configInjections);

        List<ReportItem> items = new ArrayList<>();
        List<ResolvedFeatureScope> resolvedFeatures = new ArrayList<>();
        Map<String, MemberConfigDerivation> membersById = new TreeMap<>();
        for (ResolvedFeatureScope member : includedFeatures) {
            if (CurationReport.SOURCE_TECHNICAL.equals(member.membershipSource())) {
                resolvedFeatures.add(member);
                continue;
            }
            FeatureCandidate candidate = candidatesById.get(member.candidateId());
            Map<String, DerivedCandidate> derived = deriveCandidates(member, candidate, configInjections, injectionSitesByKey, configDefaults,
                    namespacesByCandidate);
            MemberResolution resolution = mergeByPrecedence(member, derived, items);
            resolvedFeatures.add(member.withArtifactMappings(resolution.mappings()));
            membersById.put(member.id(), new MemberConfigDerivation(member.id(), member.candidateId(), resolution.keys()));
        }
        return new Result(List.copyOf(resolvedFeatures), new ConfigDerivationReport(List.copyOf(membersById.values())), List.copyOf(items));
    }

    /** Precedence-merge outcome of one member. */
    private record MemberResolution(List<MappingHint> mappings, List<ConfigKeyResolution> keys) {
    }

    /**
     * Applies the per-key precedence for one member: manifest include entries confirm or add keys in declaration
     * order, exclude entries reject derived keys, and the remaining derived deployment inputs emit non-secret keys
     * before secret keys, each sorted. Tunables are listed as skipped.
     *
     * @param member resolved member semantics.
     * @param derived derived candidate keys of the member.
     * @param items diagnostics sink.
     * @return resolved mappings and key resolutions.
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
        for (EmittedKey key : emitted.values()) {
            mappings.add(new MappingHint(ArtifactMappingTargets.OVERLAY_TARGET, key.resolution().key(), ArtifactMappingSource.ENVIRONMENT, null, null,
                    key.mappingSecret()));
            keys.add(key.resolution());
        }
        keys.addAll(skippedTunables(member, derived, emitted, rejected, items));
        keys.addAll(rejected);
        return new MemberResolution(List.copyOf(mappings), List.copyOf(keys));
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
        String entryLabel = "features[id=" + member.id() + "].configuration entry for key '" + entry.key() + "'";
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
     * @param member resolved member semantics.
     * @param candidate extracted candidate of the member, or null.
     * @param configInjections guarded injection sites of the scan.
     * @param injectionSitesByKey all injection sites per key.
     * @param configDefaults scanned YAML defaults.
     * @param namespacesByCandidate enabled-key namespaces per candidate id.
     * @return derived candidates keyed and sorted by key.
     */
    private Map<String, DerivedCandidate> deriveCandidates(ResolvedFeatureScope member, FeatureCandidate candidate,
            List<ExtractedConfigInjection> configInjections, Map<String, List<ExtractedConfigInjection>> injectionSitesByKey,
            ExtractedConfigurationDefaults configDefaults, Map<String, String> namespacesByCandidate) {
        if (candidate == null) {
            return Map.of();
        }
        Map<String, List<EvidenceItem>> evidenceByKey = new TreeMap<>();
        String conditionClass = candidate.serverConditionClass();
        if (conditionClass != null) {
            collectGuardedFileKeys(candidate, conditionClass, configInjections, injectionSitesByKey, configDefaults, evidenceByKey);
        }
        String ownNamespace = namespacesByCandidate.get(candidate.id());
        if (ownNamespace != null) {
            for (String key : configDefaults.occurrencesByKey().keySet()) {
                if (key.startsWith(ownNamespace + ".")) {
                    evidenceByKey.computeIfAbsent(key, unused -> new ArrayList<>());
                }
            }
        }
        evidenceByKey.remove(candidate.configKey());
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
     * Collects candidate keys from the files guarded by the member's condition class: injected keys whose every
     * injection site carries the guard, and YAML keys under the prefixes such files declare.
     *
     * @param candidate extracted candidate of the member.
     * @param conditionClass simple name of the member's condition class.
     * @param configInjections guarded injection sites of the scan.
     * @param injectionSitesByKey all injection sites per key.
     * @param configDefaults scanned YAML defaults.
     * @param evidenceByKey evidence sink per candidate key, updated in place.
     */
    private void collectGuardedFileKeys(FeatureCandidate candidate, String conditionClass, List<ExtractedConfigInjection> configInjections,
            Map<String, List<ExtractedConfigInjection>> injectionSitesByKey, ExtractedConfigurationDefaults configDefaults,
            Map<String, List<EvidenceItem>> evidenceByKey) {
        for (ExtractedConfigInjection injection : configInjections) {
            if (!injection.conditionGuards().contains(conditionClass)) {
                continue;
            }
            for (String key : injection.valueKeys()) {
                if (everySiteGuardedBy(injectionSitesByKey.get(key), conditionClass)) {
                    evidenceByKey.computeIfAbsent(key, unused -> new ArrayList<>()).add(new EvidenceItem(candidate.id(),
                            EvidenceItem.KIND_USAGE_CONFIG_INJECTION, injection.file(), null, key, "@Value placeholder under @Conditional(" + conditionClass + ")"));
                }
            }
            for (ExtractedConfigInjection.ConfigurationPropertiesPrefix prefix : injection.propertyPrefixes()) {
                for (String key : configDefaults.occurrencesByKey().keySet()) {
                    if (key.startsWith(prefix.prefix() + ".")) {
                        evidenceByKey.computeIfAbsent(key, unused -> new ArrayList<>()).add(new EvidenceItem(candidate.id(),
                                EvidenceItem.KIND_USAGE_CONFIG_PREFIX, injection.file(), null, prefix.prefix(),
                                "@ConfigurationProperties prefix declared by " + prefix.declaringType() + " under @Conditional(" + conditionClass + ")"));
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
     * @param conditionClass simple name of the member's condition class.
     * @return true when the key is attributable to the member.
     */
    private boolean everySiteGuardedBy(List<ExtractedConfigInjection> sites, String conditionClass) {
        return sites != null && !sites.isEmpty() && sites.stream().allMatch(site -> site.conditionGuards().contains(conditionClass));
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
