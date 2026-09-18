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
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.TechnicalEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;

/**
 * Resolves the membership of every extracted candidate and the semantics of every member. Membership comes from a
 * features entry, whose id is the Artemis module id and implies the anchor {@code module:<id>}, or from a technical
 * manifest entry with an explicit anchor; exclusion comes from a notModeled entry. The gate is tiered by feature shape:
 * a module candidate Artemis itself enumerates or displays as a feature blocks the run when nobody decided about it,
 * any other undecided candidate is listed as information. Every finding becomes a report item rather than aborting the
 * run, so one run reports every gap at once; {@link ManifestConformanceService} turns the errors into the blocking
 * verdict, and statically detectable authoring errors are rejected earlier by
 * {@link de.tum.cit.aet.artemis.featuremodel.extraction.pipeline.FeatureManifestLoader}.
 */
class ScopeCurationService {

    private static final String KIND_TECHNICAL_FEATURE = "feature";

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
     * @param anchor declared or implied manifest anchor.
     * @param semantics manifest semantics of a member, otherwise null.
     * @param profiles Spring profile tokens of a technical member, empty otherwise.
     * @param notModeled exclusion entry, otherwise null.
     */
    private record Membership(String source, String anchor, FeatureEntry semantics, List<String> profiles, NotModeledEntry notModeled) {
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

        List<CurationDecision> decisions = new ArrayList<>();
        List<ResolvedFeatureScope> includedFeatures = new ArrayList<>();
        for (FeatureCandidate candidate : candidates) {
            classifyCandidate(candidate, membershipByCandidate.get(candidate.id()), decisions, includedFeatures, items);
        }

        includedFeatures.sort(Comparator.comparing(ResolvedFeatureScope::candidateId));
        reportResolvedSemanticConflicts(manifest, includedFeatures, items);
        decisions.sort(Comparator.comparingInt((CurationDecision decision) -> stateOrder(decision.state())).thenComparing(CurationDecision::candidateId));
        CurationReport report = assembleReport(manifest, decisions, artemisCommit);
        return new Result(report, List.copyOf(includedFeatures), List.copyOf(items));
    }

    /**
     * Resolves the technical anchors, the implied features anchors, and the notModeled anchors onto candidates. An
     * anchor that resolves to no candidate or to several candidates yields an orphan diagnostic and is skipped; several
     * entries resolving to the same candidate yield a conflict diagnostic and the first entry wins.
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
                membershipByCandidate.put(candidateId, new Membership(CurationReport.SOURCE_TECHNICAL, entry.anchor(), entry.feature(), entry.profiles(), null));
            }
        }
        for (FeatureEntry entry : manifest.features()) {
            String anchor = FeatureScopeManifest.moduleAnchor(entry.id());
            String candidateId = resolveAnchor(anchor, resolver, items);
            if (candidateId != null && !conflictsWithExistingEntry(membershipByCandidate, candidateId, anchor, items)) {
                membershipByCandidate.put(candidateId, new Membership(CurationReport.SOURCE_FEATURES, anchor, entry, List.of(), null));
            }
        }
        for (NotModeledEntry entry : manifest.notModeled()) {
            String candidateId = resolveAnchor(entry.anchor(), resolver, items);
            if (candidateId != null && !conflictsWithExistingEntry(membershipByCandidate, candidateId, entry.anchor(), items)) {
                membershipByCandidate.put(candidateId, new Membership(CurationReport.SOURCE_NOT_MODELED, entry.anchor(), null, List.of(), entry));
                reportIncompleteExclusionDocumentation(resolver.candidate(candidateId), entry, items);
            }
        }
        return membershipByCandidate;
    }

    /**
     * Resolves one manifest anchor and reports resolution failures as orphan diagnostics.
     *
     * @param anchor declared or implied manifest anchor.
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
     * Classifies one candidate and, for members, resolves the semantics from the manifest entry.
     *
     * @param candidate extracted candidate.
     * @param membership resolved membership, or null when nobody decided about the candidate.
     * @param decisions decision sink.
     * @param includedFeatures resolved member semantics sink.
     * @param items report item sink.
     */
    private void classifyCandidate(FeatureCandidate candidate, Membership membership, List<CurationDecision> decisions, List<ResolvedFeatureScope> includedFeatures,
            List<ReportItem> items) {
        if (membership == null) {
            classifyUndecided(candidate, decisions, items);
            return;
        }
        if (membership.notModeled() != null) {
            decisions.add(new CurationDecision(candidate.id(), candidate.kind(), CurationReport.STATE_EXCLUDE, null, membership.notModeled().reason(),
                    membership.source()));
            return;
        }
        FeatureEntry semantics = membership.semantics();
        decisions.add(new CurationDecision(candidate.id(), candidate.kind(), CurationReport.STATE_INCLUDE, semantics.id(), null, membership.source()));
        if (FeatureCandidate.KIND_RUNTIME_TOGGLE.equals(candidate.kind()) && semantics.rationale() == null) {
            items.add(ReportItem.error(ReportItem.CODE_MANIFEST_CURATION_CONFLICT, candidate.id(),
                    "Runtime toggle member '" + semantics.id() + "' has no rationale; every modeled toggle must document its reasoning."));
        }
        includedFeatures.add(resolveSemantics(candidate, membership));
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
                    + "entry decides about it; add a features entry keyed by the module id or list it in notModeled."));
            return;
        }
        decisions.add(new CurationDecision(candidate.id(), candidate.kind(), CurationReport.STATE_UNMODELED, null, null, CurationReport.SOURCE_UNMODELED));
        items.add(ReportItem.info(ReportItem.CODE_UNMODELED_ANCHOR, candidate.id(),
                "Candidate has no decision and stays outside the model; add a features, technical, or notModeled entry to model or exclude it."));
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
        if (!manifest.declaresRoot()) {
            resolvedIds.add(FeatureScopeManifest.IMPLICIT_ROOT_ID);
        }
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
     * Combines the membership of a candidate with the modeling semantics of its manifest entry. Technical members are
     * features of category technical by section; functional members take their kind from the candidate kind and their
     * declared category.
     *
     * @param candidate extracted candidate.
     * @param membership resolved membership.
     * @return resolved semantics.
     */
    private ResolvedFeatureScope resolveSemantics(FeatureCandidate candidate, Membership membership) {
        FeatureEntry semantics = membership.semantics();
        boolean technical = CurationReport.SOURCE_TECHNICAL.equals(membership.source());
        String optionality = semantics.optionality() == null ? FeatureScopeManifest.OPTIONALITY_OPTIONAL : semantics.optionality();
        String kind = technical ? KIND_TECHNICAL_FEATURE : kind(candidate);
        String category = technical ? FeatureScopeManifest.CATEGORY_TECHNICAL : semantics.category();
        return new ResolvedFeatureScope(candidate.id(), semantics.id(), semantics.group(), semantics.parent(), kind, optionality, category,
                semantics.defaultState(), semantics.order(), List.of(), membership.profiles(), List.of(), semantics.configuration(), semantics.name(),
                semantics.description(), semantics.documentationUrl(), membership.source());
    }

    /**
     * Derives the model kind of a functional member from the extraction candidate kind.
     *
     * @param candidate extracted candidate.
     * @return model kind.
     */
    private String kind(FeatureCandidate candidate) {
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
