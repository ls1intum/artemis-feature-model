package de.tum.cit.aet.artemis.featuremodel.extraction.scan;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigurationDefaults;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;

/**
 * Immutable input boundary for one candidate-assembly invocation. Raw scanner facts stay grouped by their owning
 * scanner while the assembler facade no longer accepts or retains a positional field set across calls.
 *
 * @param source read-only source boundary used for profile-YAML existence evidence.
 * @param serverConstants server constants facts.
 * @param configHelper configuration-helper facts.
 * @param conditions Spring condition facts.
 * @param serverToggles server runtime-toggle enum facts.
 * @param clientConstants client module/profile constants.
 * @param clientToggles client runtime-toggle enum facts.
 * @param adminPage administrator feature-page facts.
 * @param featureTexts client feature translations.
 * @param configurationDefaults configuration default facts.
 * @param composeFiles Docker Compose facts.
 * @param usageEvidence source usage sites.
 */
record CandidateAssemblyInput(ArtemisSourceRepository source, ServerConstantScan.Result serverConstants, ConfigHelperScan.Result configHelper,
        ConditionClassScan.Result conditions, ServerFeatureEnumScan.Result serverToggles, ClientConstantScan.Result clientConstants,
        ClientToggleEnumScan.Result clientToggles, AdminPageScan.Result adminPage, FeatureI18nScan.Result featureTexts,
        ExtractedConfigurationDefaults configurationDefaults, ComposeFileScan.Result composeFiles, UsageEvidenceScan.Result usageEvidence) {
}
