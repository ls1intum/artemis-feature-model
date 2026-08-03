package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;

/** Assembles the mirrored backend/frontend runtime-toggle family, including usage and mirror diagnostics. */
final class RuntimeToggleCandidateAssembler {

    private static final String TOGGLE_DOC_IDENTIFIER_PREFIX = ArtemisSourceConventions.Symbols.FRONTEND_TOGGLE_REFERENCE_PREFIX;

    /**
     * Assembles runtime-toggle candidates in enum order.
     *
     * @param input complete candidate-assembly input.
     * @param context invocation-local evidence and diagnostic context.
     * @return runtime-toggle candidates.
     */
    List<FeatureCandidate> assemble(CandidateAssemblyInput input, CandidateAssemblyContext context) {
        Map<String, BackendFeatureEnumScan.ScannedEnumMember> backendMembers = new LinkedHashMap<>();
        input.backendToggles().members().forEach(member -> backendMembers.putIfAbsent(member.name(), member));
        Map<String, FrontendToggleEnumScan.ScannedToggleMember> frontendMembers = new LinkedHashMap<>();
        input.frontendToggles().members().forEach(member -> frontendMembers.putIfAbsent(member.name(), member));

        Set<String> toggleNames = new LinkedHashSet<>();
        toggleNames.addAll(backendMembers.keySet());
        toggleNames.addAll(frontendMembers.keySet());

        Map<String, String> documentationLinks = documentationLinks(input, context, toggleNames);
        List<FeatureCandidate> candidates = emitCandidates(input, context, backendMembers, frontendMembers, toggleNames, documentationLinks);
        attachUsageEvidence(input, context, toggleNames);
        return candidates;
    }

    /** Collects documentation links from computed admin-page keys. */
    private Map<String, String> documentationLinks(CandidateAssemblyInput input, CandidateAssemblyContext context, Set<String> toggleNames) {
        Map<String, String> links = new LinkedHashMap<>();
        for (AdminPageScan.DocumentationEntry entry : input.adminPage().documentationEntries()) {
            if (entry.identifier().startsWith(TOGGLE_DOC_IDENTIFIER_PREFIX)) {
                String toggleName = entry.identifier().substring(TOGGLE_DOC_IDENTIFIER_PREFIX.length());
                if (toggleNames.contains(toggleName)) {
                    links.putIfAbsent(toggleName, entry.url());
                    context.addEvidence(FeatureCandidate.NAMESPACE_TOGGLE + toggleName, EvidenceItem.KIND_ADMIN_PAGE, input.adminPage().file(), entry.line(),
                            entry.identifier(), entry.url());
                }
            }
        }
        return links;
    }

    /** Emits candidates and frontend/backend mirror diagnostics. */
    private List<FeatureCandidate> emitCandidates(CandidateAssemblyInput input, CandidateAssemblyContext context,
            Map<String, BackendFeatureEnumScan.ScannedEnumMember> backendMembers,
            Map<String, FrontendToggleEnumScan.ScannedToggleMember> frontendMembers, Set<String> toggleNames, Map<String, String> documentationLinks) {
        List<FeatureCandidate> candidates = new ArrayList<>();
        for (String name : toggleNames) {
            String candidateId = FeatureCandidate.NAMESPACE_TOGGLE + name;
            BackendFeatureEnumScan.ScannedEnumMember backendMember = backendMembers.get(name);
            FrontendToggleEnumScan.ScannedToggleMember frontendMember = frontendMembers.get(name);
            if (backendMember != null) {
                context.addEvidence(candidateId, EvidenceItem.KIND_BACKEND_ENUM, input.backendToggles().file(), backendMember.line(), name, null);
            }
            else {
                context.addItem(ReportItem.error(ReportItem.CODE_FE_BE_MIRROR_MISMATCH, candidateId,
                        "Runtime toggle '" + name + "' exists in the frontend FeatureToggle enum but not in the backend Feature enum."));
            }
            if (frontendMember != null) {
                context.addEvidence(candidateId, EvidenceItem.KIND_FRONTEND_ENUM, input.frontendToggles().file(), frontendMember.line(), name, null);
            }
            else {
                context.addItem(ReportItem.error(ReportItem.CODE_FE_BE_MIRROR_MISMATCH, candidateId,
                        "Runtime toggle '" + name + "' exists in the backend Feature enum but not in the frontend FeatureToggle enum."));
            }
            FeatureI18nScan.FeatureTexts texts = input.featureTexts().toggleTexts().get(name);
            if (texts != null) {
                context.addEvidence(candidateId, EvidenceItem.KIND_I18N, input.featureTexts().file(), null, "artemisApp.features.toggles." + name, null);
            }
            candidates.add(new FeatureCandidate(candidateId, FeatureCandidate.KIND_RUNTIME_TOGGLE, texts == null ? null : texts.name(),
                    texts == null ? null : texts.description(), texts == null ? null : texts.disableWarning(), null, null,
                    backendMember == null ? null : "Feature." + name, frontendMember == null ? null : "FeatureToggle." + name, null, null, null,
                    frontendMember != null, documentationLinks.get(name)));
        }
        return candidates;
    }

    /** Attaches backend annotation and frontend template usage evidence. */
    private void attachUsageEvidence(CandidateAssemblyInput input, CandidateAssemblyContext context, Set<String> toggleNames) {
        for (UsageEvidenceScan.UsageSite site : input.usageEvidence().featureToggleSites()) {
            if (toggleNames.contains(site.symbol())) {
                context.addEvidence(FeatureCandidate.NAMESPACE_TOGGLE + site.symbol(), EvidenceItem.KIND_USAGE_FEATURE_TOGGLE, site.file(), site.line(),
                        "Feature." + site.symbol(), null);
            }
        }
        for (UsageEvidenceScan.UsageSite site : input.usageEvidence().templateToggleSites()) {
            if (toggleNames.contains(site.symbol())) {
                context.addEvidence(FeatureCandidate.NAMESPACE_TOGGLE + site.symbol(), EvidenceItem.KIND_USAGE_TEMPLATE, site.file(), site.line(),
                        "FeatureToggle." + site.symbol(), null);
            }
        }
    }
}
