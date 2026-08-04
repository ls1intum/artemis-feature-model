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

/** Assembles the mirrored server/client runtime-toggle family, including usage and mirror diagnostics. */
final class RuntimeToggleCandidateAssembler {

    private static final String TOGGLE_DOC_IDENTIFIER_PREFIX = ArtemisSourceConventions.Symbols.CLIENT_TOGGLE_REFERENCE_PREFIX;

    /**
     * Assembles runtime-toggle candidates in enum order.
     *
     * @param input complete candidate-assembly input.
     * @param context invocation-local evidence and diagnostic context.
     * @return runtime-toggle candidates.
     */
    List<FeatureCandidate> assemble(CandidateAssemblyInput input, CandidateAssemblyContext context) {
        Map<String, ServerFeatureEnumScan.ScannedEnumMember> serverMembers = new LinkedHashMap<>();
        input.serverToggles().members().forEach(member -> serverMembers.putIfAbsent(member.name(), member));
        Map<String, ClientToggleEnumScan.ScannedToggleMember> clientMembers = new LinkedHashMap<>();
        input.clientToggles().members().forEach(member -> clientMembers.putIfAbsent(member.name(), member));

        Set<String> toggleNames = new LinkedHashSet<>();
        toggleNames.addAll(serverMembers.keySet());
        toggleNames.addAll(clientMembers.keySet());

        Map<String, String> documentationLinks = documentationLinks(input, context, toggleNames);
        List<FeatureCandidate> candidates = emitCandidates(input, context, serverMembers, clientMembers, toggleNames, documentationLinks);
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

    /** Emits candidates and client/server mirror diagnostics. */
    private List<FeatureCandidate> emitCandidates(CandidateAssemblyInput input, CandidateAssemblyContext context,
            Map<String, ServerFeatureEnumScan.ScannedEnumMember> serverMembers,
            Map<String, ClientToggleEnumScan.ScannedToggleMember> clientMembers, Set<String> toggleNames, Map<String, String> documentationLinks) {
        List<FeatureCandidate> candidates = new ArrayList<>();
        for (String name : toggleNames) {
            String candidateId = FeatureCandidate.NAMESPACE_TOGGLE + name;
            ServerFeatureEnumScan.ScannedEnumMember serverMember = serverMembers.get(name);
            ClientToggleEnumScan.ScannedToggleMember clientMember = clientMembers.get(name);
            if (serverMember != null) {
                context.addEvidence(candidateId, EvidenceItem.KIND_SERVER_ENUM, input.serverToggles().file(), serverMember.line(), name, null);
            }
            else {
                context.addItem(ReportItem.error(ReportItem.CODE_CLIENT_SERVER_MIRROR_MISMATCH, candidateId,
                        "Runtime toggle '" + name + "' exists in the client FeatureToggle enum but not in the server Feature enum."));
            }
            if (clientMember != null) {
                context.addEvidence(candidateId, EvidenceItem.KIND_CLIENT_ENUM, input.clientToggles().file(), clientMember.line(), name, null);
            }
            else {
                context.addItem(ReportItem.error(ReportItem.CODE_CLIENT_SERVER_MIRROR_MISMATCH, candidateId,
                        "Runtime toggle '" + name + "' exists in the server Feature enum but not in the client FeatureToggle enum."));
            }
            FeatureI18nScan.FeatureTexts texts = input.featureTexts().toggleTexts().get(name);
            if (texts != null) {
                context.addEvidence(candidateId, EvidenceItem.KIND_I18N, input.featureTexts().file(), null, "artemisApp.features.toggles." + name, null);
            }
            candidates.add(new FeatureCandidate(candidateId, FeatureCandidate.KIND_RUNTIME_TOGGLE, texts == null ? null : texts.name(),
                    texts == null ? null : texts.description(), texts == null ? null : texts.disableWarning(), null, null,
                    serverMember == null ? null : "Feature." + name, clientMember == null ? null : "FeatureToggle." + name, null, null, null,
                    clientMember != null, documentationLinks.get(name)));
        }
        return candidates;
    }

    /** Attaches server annotation and client template usage evidence. */
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
