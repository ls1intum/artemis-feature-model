package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;

/** Assembles deployment-oriented Spring profile and database-infrastructure candidate families. */
final class DeploymentCandidateAssembler {

    private static final String PROFILE_CONSTANT_PREFIX = ArtemisSourceConventions.Symbols.PROFILE_CONSTANT_PREFIX;

    /**
     * Assembles profile candidates followed by infrastructure candidates.
     *
     * @param input complete candidate-assembly input.
     * @param context invocation-local evidence context.
     * @return deployment-oriented candidates.
     */
    List<FeatureCandidate> assemble(CandidateAssemblyInput input, CandidateAssemblyContext context) {
        List<FeatureCandidate> candidates = new ArrayList<>();
        candidates.addAll(assembleProfiles(input, context));
        candidates.addAll(assembleInfrastructure(input, context));
        return candidates;
    }

    /** Emits Spring profile candidates from backend/frontend constants and profile evidence. */
    private List<FeatureCandidate> assembleProfiles(CandidateAssemblyInput input, CandidateAssemblyContext context) {
        Map<String, BackendConstantScan.ScannedConstant> backendProfiles = new LinkedHashMap<>();
        input.backendConstants().constants().stream().filter(constant -> constant.name().startsWith(PROFILE_CONSTANT_PREFIX))
                .forEach(constant -> backendProfiles.putIfAbsent(constant.value(), constant));
        Map<String, FrontendConstantScan.ScannedFrontendConstant> frontendProfiles = new LinkedHashMap<>();
        input.frontendConstants().constants().stream().filter(constant -> constant.name().startsWith(PROFILE_CONSTANT_PREFIX))
                .forEach(constant -> frontendProfiles.putIfAbsent(constant.value(), constant));

        Map<String, String> frontendValuesByName = new LinkedHashMap<>();
        input.frontendConstants().constants().forEach(constant -> frontendValuesByName.putIfAbsent(constant.name(), constant.value()));
        Set<String> displayedProfiles = new LinkedHashSet<>();
        Map<String, AdminPageScan.MembershipEntry> displayedProfileEntries = new LinkedHashMap<>();
        for (AdminPageScan.MembershipEntry entry : input.adminPage().displayedProfiles()) {
            String value = frontendValuesByName.get(entry.identifier());
            if (value != null) {
                displayedProfiles.add(value);
                displayedProfileEntries.putIfAbsent(value, entry);
            }
        }
        Map<String, AdminPageScan.DocumentationEntry> profileDocumentation = new LinkedHashMap<>();
        for (AdminPageScan.DocumentationEntry entry : input.adminPage().documentationEntries()) {
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
                context.addEvidence(candidateId, EvidenceItem.KIND_BACKEND_CONSTANT, input.backendConstants().file(), backendConstant.line(),
                        backendConstant.name(), null);
            }
            if (frontendConstant != null) {
                context.addEvidence(candidateId, EvidenceItem.KIND_FRONTEND_CONSTANT, input.frontendConstants().file(), frontendConstant.line(),
                        frontendConstant.name(), null);
            }
            String profileYaml = ArtemisSourceConventions.Files.profileConfiguration(profileId);
            if (input.source().fileExists(profileYaml)) {
                context.addEvidence(candidateId, EvidenceItem.KIND_PROFILE_YAML, profileYaml, null, profileId, null);
            }
            if ("jenkins".equals(profileId) && input.composeFiles().jenkinsComposeFile() != null) {
                context.addEvidence(candidateId, EvidenceItem.KIND_COMPOSE_FILE, input.composeFiles().jenkinsComposeFile(), null, profileId, null);
            }
            FeatureI18nScan.FeatureTexts texts = input.featureTexts().profileTexts().get(profileId);
            if (texts != null) {
                context.addEvidence(candidateId, EvidenceItem.KIND_I18N, input.featureTexts().file(), null, "artemisApp.features.profiles." + profileId, null);
            }
            AdminPageScan.MembershipEntry membershipEntry = displayedProfileEntries.get(profileId);
            if (membershipEntry != null) {
                context.addEvidence(candidateId, EvidenceItem.KIND_ADMIN_PAGE, input.adminPage().file(), membershipEntry.line(), membershipEntry.identifier(),
                        "display membership");
            }
            AdminPageScan.DocumentationEntry documentationEntry = profileDocumentation.get(profileId);
            candidates.add(new FeatureCandidate(candidateId, FeatureCandidate.KIND_SPRING_PROFILE, texts == null ? null : texts.name(),
                    texts == null ? null : texts.description(), null, null, null, backendConstant == null ? null : backendConstant.name(),
                    frontendConstant == null ? null : frontendConstant.name(), null, profileId, null, displayedProfiles.contains(profileId),
                    documentationEntry == null ? null : documentationEntry.url()));
        }
        return candidates;
    }

    /** Emits database candidates from paired top-level Compose alternatives. */
    private List<FeatureCandidate> assembleInfrastructure(CandidateAssemblyInput input, CandidateAssemblyContext context) {
        Map<String, List<ComposeFileScan.ComposeAlternative>> alternativesByDatabase = new TreeMap<>();
        for (ComposeFileScan.ComposeAlternative alternative : input.composeFiles().alternatives()) {
            String databaseId = ComposeFileScan.MYSQL_TOKEN.equals(alternative.databaseToken()) ? "mysql" : "postgres";
            alternativesByDatabase.computeIfAbsent(databaseId, unused -> new ArrayList<>()).add(alternative);
        }
        List<FeatureCandidate> candidates = new ArrayList<>();
        alternativesByDatabase.forEach((databaseId, alternatives) -> {
            String candidateId = FeatureCandidate.NAMESPACE_INFRASTRUCTURE + databaseId;
            for (ComposeFileScan.ComposeAlternative alternative : alternatives) {
                String detail = alternative.pairedFile() == null ? "no paired alternative found" : "paired with " + alternative.pairedFile();
                context.addEvidence(candidateId, EvidenceItem.KIND_COMPOSE_FILE, alternative.file(), null, databaseId, detail);
            }
            candidates.add(new FeatureCandidate(candidateId, FeatureCandidate.KIND_INFRASTRUCTURE, null,
                    "Database alternative encoded by paired Docker compose stacks.", null, null, null, null, null, null, null, null, null, null));
        });
        return candidates;
    }
}
