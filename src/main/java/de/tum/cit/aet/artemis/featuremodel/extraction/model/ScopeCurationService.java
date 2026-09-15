package de.tum.cit.aet.artemis.featuremodel.extraction.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport.CurationDecision;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.FeatureEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.NotModeledEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ProvisionalEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.TechnicalEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;

/**
 * Resolves the membership of every extracted candidate and the semantics of every member. Membership comes from a
 * provisional manifest entry for functional features or from a technical manifest entry; exclusion comes from a
 * notModeled entry. The gate is tiered by
 * feature shape: a module candidate Artemis itself enumerates or displays as a feature blocks the run when nobody
 * decided about it, any other undecided candidate is listed as information. Every finding becomes a report item
 * rather than aborting the run, so one run reports every gap at once; {@link ManifestConformanceService} turns the
 * errors into the blocking verdict, and statically detectable authoring errors are rejected earlier by
 * {@link de.tum.cit.aet.artemis.featuremodel.extraction.pipeline.FeatureManifestLoader}.
 */
class ScopeCurationService {

    /**
     * Curation result.
     *
     * @param report structured curation section.
     * @param includedFeatures resolved member semantics sorted by candidate id.
     * @param items curation diagnostics.
     */
    record Result(CurationReport report, List<ResolvedFeatureScope> includedFeatures, List<ReportItem> items) {
    }

    /**
     * Resolved membership of one candidate.
     *
     * @param source one of the {@code CurationReport.SOURCE_*} membership constants.
     * @param anchor manifest anchor that declared the membership.
     * @param id feature id for members, otherwise null.
     * @param semantics inline semantics of a technical member, otherwise null.
     * @param notModeled exclusion entry, otherwise null.
     */
    private record Membership(String source, String anchor, String id, FeatureEntry semantics, NotModeledEntry notModeled) {
    }

    /**
     * Resolves membership and semantics for the extracted candidates.
     *
     * @param manifest loaded manifest.
     * @param candidates extracted candidates.
     * @param artemisCommit derived source revision the scan was taken from.
     * @return classifications, resolved member semantics, and diagnostics.
     */
    Result curate(FeatureScopeManifest manifest, List<FeatureCandidate> candidates, String artemisCommit) {
        List<ReportItem> items = new ArrayList<>();
        CandidateResolver resolver = new CandidateResolver(candidates);
        Map<String, Membership> membershipByCandidate = resolveManifestMembership(manifest, resolver, items);
        Map<String, FeatureEntry> featuresById = new LinkedHashMap<>();
        manifest.features().forEach(entry -> featuresById.put(entry.id(), entry));

        List<CurationDecision> decisions = new ArrayList<>();
        List<ResolvedFeatureScope> includedFeatures = new ArrayList<>();
        for (FeatureCandidate candidate : candidates) {
            classifyCandidate(candidate, membershipByCandidate.get(candidate.id()), featuresById, decisions, includedFeatures, items);
        }
        reportUnknownFeatureEntries(manifest, membershipByCandidate, items);

        includedFeatures.sort(Comparator.comparing(ResolvedFeatureScope::candidateId));
        reportResolvedSemanticConflicts(manifest, includedFeatures, items);
        decisions.sort(Comparator.comparingInt((CurationDecision decision) -> stateOrder(decision.state())).thenComparing(CurationDecision::candidateId));
        CurationReport report = assembleReport(manifest, decisions, artemisCommit);
        return new Result(report, List.copyOf(includedFeatures), List.copyOf(items));
    }

    /**
     * Resolves the technical, provisional, and notModeled anchors onto candidates. An anchor that resolves to no
     * candidate or to several candidates yields an orphan diagnostic and is skipped; several entries resolving to the
     * same candidate yield a conflict diagnostic and the first entry wins.
     *
     * @param manifest loaded manifest.
     * @param resolver candidate resolver.
     * @param items report item sink.
     * @return membership per resolved candidate id.
     */
    private Map<String, Membership> resolveManifestMembership(FeatureScopeManifest manifest, CandidateResolver resolver, List<ReportItem> items) {
        Map<String, Membership> membershipByCandidate = new LinkedHashMap<>();
        for (TechnicalEntry entry : manifest.technical()) {
            String candidateId = resolveAnchor(entry.anchor(), resolver, items);
            if (candidateId != null && !conflictsWithExistingEntry(membershipByCandidate, candidateId, entry.anchor(), items)) {
                membershipByCandidate.put(candidateId, new Membership(CurationReport.SOURCE_TECHNICAL, entry.anchor(), entry.feature().id(), entry.feature(), null));
            }
        }
        for (ProvisionalEntry entry : manifest.provisional()) {
            String candidateId = resolveAnchor(entry.anchor(), resolver, items);
            if (candidateId != null && !conflictsWithExistingEntry(membershipByCandidate, candidateId, entry.anchor(), items)) {
                membershipByCandidate.put(candidateId, new Membership(CurationReport.SOURCE_PROVISIONAL, entry.anchor(), entry.id(), null, null));
            }
        }
        for (NotModeledEntry entry : manifest.notModeled()) {
            String candidateId = resolveAnchor(entry.anchor(), resolver, items);
            if (candidateId != null && !conflictsWithExistingEntry(membershipByCandidate, candidateId, entry.anchor(), items)) {
                membershipByCandidate.put(candidateId, new Membership(CurationReport.SOURCE_NOT_MODELED, entry.anchor(), null, null, entry));
                reportIncompleteExclusionDocumentation(resolver.candidate(candidateId), entry, items);
            }
        }
        return membershipByCandidate;
    }

    /**
     * Resolves one manifest anchor and reports resolution failures as orphan diagnostics.
     *
     * @param anchor manifest anchor.
     * @param resolver candidate resolver.
     * @param items report item sink.
     * @return resolved candidate id, or null when the anchor is unknown or ambiguous.
     */
    private String resolveAnchor(String anchor, CandidateResolver resolver, List<ReportItem> items) {
        CandidateResolver.Resolution resolution = resolver.resolve(anchor);
        if (resolution.problem() != null) {
            items.add(ReportItem.error(ReportItem.CODE_MANIFEST_ORPHAN_ANCHOR, anchor, resolution.problem() + " The entry is skipped for this scan."));
            return null;
        }
        return resolution.candidateId();
    }

    /**
     * Reports a conflict when a candidate already has a manifest entry.
     *
     * @param membershipByCandidate memberships resolved so far.
     * @param candidateId resolved candidate id.
     * @param anchor anchor of the newly resolved entry.
     * @param items report item sink.
     * @return true if the candidate was already claimed and the new entry must be skipped.
     */
    private boolean conflictsWithExistingEntry(Map<String, Membership> membershipByCandidate, String candidateId, String anchor, List<ReportItem> items) {
        if (!membershipByCandidate.containsKey(candidateId)) {
            return false;
        }
        items.add(ReportItem.error(ReportItem.CODE_MANIFEST_CURATION_CONFLICT, candidateId,
                "Manifest anchor '" + anchor + "' resolves to a candidate that already has a manifest entry; the first entry wins."));
        return true;
    }

    /**
     * Reports optional exclusion documentation that was omitted without making the curation decision non-conformant.
     *
     * @param candidate resolved excluded candidate.
     * @param entry manifest exclusion entry.
     * @param items report item sink.
     */
    private void reportIncompleteExclusionDocumentation(FeatureCandidate candidate, NotModeledEntry entry, List<ReportItem> items) {
        if (FeatureScopeManifest.EXCLUSION_REASON_UNSPECIFIED.equals(entry.reason())) {
            items.add(ReportItem.warning(ReportItem.CODE_EXCLUSION_REASON_UNSPECIFIED, candidate.id(),
                    "Excluded candidate has no reason code; the curation report groups it under '" + FeatureScopeManifest.EXCLUSION_REASON_UNSPECIFIED + "'."));
        }
        if (FeatureCandidate.KIND_RUNTIME_TOGGLE.equals(candidate.kind()) && entry.rationale() == null) {
            items.add(ReportItem.warning(ReportItem.CODE_EXCLUDED_TOGGLE_RATIONALE_MISSING, candidate.id(),
                    "Excluded runtime toggle entry '" + entry.anchor() + "' has no rationale; document why it stays outside the model when practical."));
        }
    }

    /**
     * Classifies one candidate and, for members, resolves the semantics from the manifest.
     *
     * @param candidate extracted candidate.
     * @param membership resolved membership, or null when nobody decided about the candidate.
     * @param featuresById manifest features entries keyed by id.
     * @param decisions decision sink.
     * @param includedFeatures resolved member semantics sink.
     * @param items report item sink.
     */
    private void classifyCandidate(FeatureCandidate candidate, Membership membership, Map<String, FeatureEntry> featuresById, List<CurationDecision> decisions,
            List<ResolvedFeatureScope> includedFeatures, List<ReportItem> items) {
        if (membership == null) {
            classifyUndecided(candidate, decisions, items);
            return;
        }
        if (membership.notModeled() != null) {
            decisions.add(new CurationDecision(candidate.id(), candidate.kind(), CurationReport.STATE_EXCLUDE, null, membership.notModeled().reason(),
                    membership.source()));
            return;
        }
        decisions.add(new CurationDecision(candidate.id(), candidate.kind(), CurationReport.STATE_INCLUDE, membership.id(), null, membership.source()));
        FeatureEntry semantics = membership.semantics() != null ? membership.semantics() : featuresById.get(membership.id());
        if (semantics == null) {
            items.add(ReportItem.error(ReportItem.CODE_MEMBER_UNPLACED, candidate.id(), "Member '" + membership.id() + "' declared by "
                    + membership.source() + " anchor '" + membership.anchor() + "' has no features entry; add features[id=" + membership.id() + "] with its placement."));
            return;
        }
        if (FeatureCandidate.KIND_RUNTIME_TOGGLE.equals(candidate.kind()) && semantics.rationale() == null) {
            items.add(ReportItem.error(ReportItem.CODE_MANIFEST_CURATION_CONFLICT, candidate.id(),
                    "Runtime toggle member '" + membership.id() + "' has no rationale; every modeled toggle must document its reasoning."));
        }
        if (CurationReport.SOURCE_PROVISIONAL.equals(membership.source())) {
            items.add(ReportItem.info(ReportItem.CODE_PROVISIONAL_MEMBERSHIP, candidate.id(),
                    "Membership of '" + membership.id() + "' is carried by provisional entry '" + membership.anchor() + "'."));
        }
        includedFeatures.add(resolveSemantics(candidate, membership.id(), semantics, membership.source()));
    }

    /**
     * Classifies a candidate nobody decided about: feature-shaped module candidates block, everything else is listed.
     *
     * @param candidate extracted candidate.
     * @param decisions decision sink.
     * @param items report item sink.
     */
    private void classifyUndecided(FeatureCandidate candidate, List<CurationDecision> decisions, List<ReportItem> items) {
        if (isFeatureShaped(candidate)) {
            decisions.add(new CurationDecision(candidate.id(), candidate.kind(), CurationReport.STATE_UNDECLARED, null, null, CurationReport.SOURCE_UNDECLARED));
            items.add(ReportItem.error(ReportItem.CODE_UNDECLARED_CANDIDATE, candidate.id(), "Artemis presents this module as a feature, but no manifest "
                    + "entry decides about it; add a provisional entry or list it in notModeled."));
            return;
        }
        decisions.add(new CurationDecision(candidate.id(), candidate.kind(), CurationReport.STATE_UNMODELED, null, null, CurationReport.SOURCE_UNMODELED));
        items.add(ReportItem.info(ReportItem.CODE_UNMODELED_ANCHOR, candidate.id(),
                "Candidate has no decision and stays outside the model; add a manifest entry to model or exclude it."));
    }

    /**
     * Decides whether Artemis itself presents a candidate as a feature: a module candidate enumerated by the server
     * or displayed on the admin Features page.
     *
     * @param candidate extracted candidate.
     * @return true when an undecided candidate must block the run.
     */
    private boolean isFeatureShaped(FeatureCandidate candidate) {
        return FeatureCandidate.KIND_MODULE_FEATURE.equals(candidate.kind())
                && (Boolean.TRUE.equals(candidate.enumeratedByServer()) || Boolean.TRUE.equals(candidate.displayedOnAdminPage()));
    }

    /**
     * Reports features entries whose id no provisional or technical entry declares as a member.
     *
     * @param manifest loaded manifest.
     * @param membershipByCandidate resolved memberships.
     * @param items report item sink.
     */
    private void reportUnknownFeatureEntries(FeatureScopeManifest manifest, Map<String, Membership> membershipByCandidate, List<ReportItem> items) {
        Set<String> memberIds = new LinkedHashSet<>();
        membershipByCandidate.values().stream().filter(membership -> membership.id() != null).forEach(membership -> memberIds.add(membership.id()));
        for (FeatureEntry entry : manifest.features()) {
            if (!memberIds.contains(entry.id())) {
                items.add(ReportItem.error(ReportItem.CODE_MANIFEST_FEATURE_UNKNOWN, entry.id(), "features entry '" + entry.id()
                        + "' matches no member: no provisional or technical entry declares this id for this scan."));
            }
        }
    }

    /**
     * Reports conflicts in the resolved member semantics: duplicate feature ids and parent or group references that
     * no longer resolve after skipped orphan entries. The manifest-internal references were already validated
     * statically by the loader, so every conflict here is scan-induced.
     *
     * @param manifest loaded manifest.
     * @param includedFeatures resolved member semantics.
     * @param items report item sink.
     */
    private void reportResolvedSemanticConflicts(FeatureScopeManifest manifest, List<ResolvedFeatureScope> includedFeatures, List<ReportItem> items) {
        Set<String> resolvedIds = new LinkedHashSet<>();
        manifest.conceptualNodes().forEach(node -> resolvedIds.add(node.id()));
        for (ResolvedFeatureScope feature : includedFeatures) {
            if (!resolvedIds.add(feature.id())) {
                items.add(ReportItem.error(ReportItem.CODE_MANIFEST_CURATION_CONFLICT, feature.candidateId(),
                        "Resolved feature id '" + feature.id() + "' is already used by another member or conceptual node."));
            }
        }
        for (ResolvedFeatureScope feature : includedFeatures) {
            reportUnresolvedReference(feature, feature.parent(), resolvedIds, items);
            reportUnresolvedReference(feature, feature.group(), resolvedIds, items);
        }
    }

    /**
     * Reports a parent or group reference that does not resolve within the member and conceptual ids.
     *
     * @param feature resolved feature carrying the reference.
     * @param reference referenced parent or group id, or null when not set.
     * @param resolvedIds all resolved feature ids.
     * @param items report item sink.
     */
    private void reportUnresolvedReference(ResolvedFeatureScope feature, String reference, Set<String> resolvedIds, List<ReportItem> items) {
        if (reference != null && !resolvedIds.contains(reference)) {
            items.add(ReportItem.error(ReportItem.CODE_MANIFEST_CURATION_CONFLICT, feature.candidateId(),
                    "Resolved feature '" + feature.id() + "' references parent/group '" + reference + "' which is not part of the resolved scope."));
        }
    }

    /**
     * Combines the id of a membership declaration with the modeling semantics of its manifest entry.
     *
     * @param candidate extracted candidate.
     * @param id feature id declared by the membership.
     * @param semantics manifest semantics of the member.
     * @param membershipSource what declared the membership.
     * @return resolved semantics.
     */
    private ResolvedFeatureScope resolveSemantics(FeatureCandidate candidate, String id, FeatureEntry semantics, String membershipSource) {
        String optionality = semantics.optionality() == null ? FeatureScopeManifest.OPTIONALITY_OPTIONAL : semantics.optionality();
        return new ResolvedFeatureScope(candidate.id(), id, semantics.group(), semantics.parent(), kind(semantics.kind(), candidate), optionality,
                semantics.category(), semantics.defaultState(), semantics.order(), semantics.requiresCapabilities(), semantics.providesCapabilities(),
                semantics.artifactMappings(), semantics.configuration(), semantics.name(), semantics.description(), semantics.documentationUrl(),
                membershipSource);
    }

    /**
     * Chooses the model kind of a member: the explicit override when present, otherwise a default derived from the
     * extraction candidate kind.
     *
     * @param declaredKind explicit kind from the manifest, or null.
     * @param candidate extracted candidate.
     * @return model kind.
     */
    private String kind(String declaredKind, FeatureCandidate candidate) {
        if (declaredKind != null) {
            return declaredKind;
        }
        return switch (candidate.kind()) {
            case FeatureCandidate.KIND_MODULE_FEATURE -> "module";
            case FeatureCandidate.KIND_RUNTIME_TOGGLE -> "runtime-toggle";
            default -> "technical";
        };
    }

    /**
     * Assembles the curation report section with deterministic counts and ordering.
     *
     * @param manifest loaded manifest.
     * @param decisions sorted candidate decisions.
     * @param artemisCommit derived source revision the scan was taken from.
     * @return curation report section.
     */
    private CurationReport assembleReport(FeatureScopeManifest manifest, List<CurationDecision> decisions, String artemisCommit) {
        Map<String, Integer> stateCounts = CurationReport.zeroStateCounts();
        Map<String, Map<String, Integer>> byKind = new TreeMap<>();
        List<String> undeclared = new ArrayList<>();
        for (CurationDecision decision : decisions) {
            stateCounts.merge(decision.state(), 1, Integer::sum);
            byKind.computeIfAbsent(decision.candidateKind(), ignored -> CurationReport.zeroStateCounts()).merge(decision.state(), 1, Integer::sum);
            if (CurationReport.STATE_UNDECLARED.equals(decision.state())) {
                undeclared.add(decision.candidateId());
            }
        }
        undeclared.sort(String::compareTo);
        return new CurationReport(manifest.manifestVersion(), artemisCommit, stateCounts, deepImmutable(byKind), List.copyOf(undeclared), List.copyOf(decisions));
    }

    /**
     * Copies nested count maps into unmodifiable views.
     *
     * @param values nested count maps.
     * @return unmodifiable deep copy.
     */
    private Map<String, Map<String, Integer>> deepImmutable(Map<String, Map<String, Integer>> values) {
        Map<String, Map<String, Integer>> copy = new LinkedHashMap<>();
        values.forEach((key, counts) -> copy.put(key, Collections.unmodifiableMap(new LinkedHashMap<>(counts))));
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Orders decisions so undeclared candidates lead the report, followed by members, exclusions, and unmodeled anchors.
     *
     * @param state decision state.
     * @return sort rank of the state.
     */
    private int stateOrder(String state) {
        return switch (state) {
            case CurationReport.STATE_UNDECLARED -> 0;
            case CurationReport.STATE_INCLUDE -> 1;
            case CurationReport.STATE_EXCLUDE -> 2;
            default -> 3;
        };
    }

    /** Resolves manifest symbols to the canonical namespaced candidate id. */
    private static final class CandidateResolver {

        /**
         * Outcome of one anchor resolution; exactly one component is set.
         *
         * @param candidateId resolved candidate id, or null on failure.
         * @param problem human-readable resolution failure, or null on success.
         */
        private record Resolution(String candidateId, String problem) {
        }

        private final Map<String, FeatureCandidate> candidatesById = new LinkedHashMap<>();

        private final List<FeatureCandidate> candidates;

        /**
         * Creates a resolver over the extracted candidates.
         *
         * @param candidates extracted candidates.
         */
        private CandidateResolver(List<FeatureCandidate> candidates) {
            this.candidates = candidates;
            candidates.forEach(candidate -> candidatesById.put(candidate.id(), candidate));
        }

        /**
         * Resolves an anchor written as a namespaced candidate id or as a source symbol: a condition class, a server
         * constant, or a client constant, optionally package-qualified.
         *
         * @param anchor manifest anchor.
         * @return successful resolution, or the failure description.
         */
        private Resolution resolve(String anchor) {
            if (candidatesById.containsKey(anchor)) {
                return new Resolution(anchor, null);
            }
            List<String> matches = candidates.stream().filter(candidate -> matches(candidate, anchor)).map(FeatureCandidate::id).distinct().toList();
            if (matches.isEmpty()) {
                return new Resolution(null, "Anchor '" + anchor + "' does not match an extraction candidate.");
            }
            if (matches.size() > 1) {
                return new Resolution(null, "Anchor '" + anchor + "' is ambiguous across candidates " + matches + ".");
            }
            return new Resolution(matches.getFirst(), null);
        }

        /**
         * Checks whether an anchor names one of the candidate's source symbols.
         *
         * @param candidate extracted candidate.
         * @param anchor manifest anchor.
         * @return true if the anchor matches a symbol of the candidate.
         */
        private boolean matches(FeatureCandidate candidate, String anchor) {
            return matchesSymbol(anchor, candidate.serverConditionClass()) || matchesSymbol(anchor, candidate.serverConstant())
                    || matchesSymbol(anchor, candidate.clientConstant());
        }

        /**
         * Checks whether an anchor equals a symbol or is a package-qualified form of it.
         *
         * @param anchor manifest anchor.
         * @param symbol candidate source symbol, or null.
         * @return true if the anchor names the symbol.
         */
        private boolean matchesSymbol(String anchor, String symbol) {
            return symbol != null && (anchor.equals(symbol) || anchor.endsWith("." + symbol));
        }

        /**
         * Returns the candidate registered under an id.
         *
         * @param id namespaced candidate id.
         * @return candidate.
         */
        private FeatureCandidate candidate(String id) {
            return candidatesById.get(id);
        }
    }
}
