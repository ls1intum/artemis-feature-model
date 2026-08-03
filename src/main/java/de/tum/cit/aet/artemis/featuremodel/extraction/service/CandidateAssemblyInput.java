package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigurationDefaults;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;

/**
 * Immutable input boundary for one candidate-assembly invocation. Raw scanner facts stay grouped by their owning
 * scanner while the assembler facade no longer accepts or retains a positional field set across calls.
 *
 * @param source read-only source boundary used for profile-YAML existence evidence.
 * @param backendConstants backend constants facts.
 * @param configHelper configuration-helper facts.
 * @param conditions Spring condition facts.
 * @param backendToggles backend runtime-toggle enum facts.
 * @param frontendConstants frontend module/profile constants.
 * @param frontendToggles frontend runtime-toggle enum facts.
 * @param adminPage administrator feature-page facts.
 * @param featureTexts frontend feature translations.
 * @param configurationDefaults configuration default facts.
 * @param composeFiles Docker Compose facts.
 * @param usageEvidence source usage sites.
 */
record CandidateAssemblyInput(ArtemisSourceRepository source, BackendConstantScan.Result backendConstants, ConfigHelperScan.Result configHelper,
        ConditionClassScan.Result conditions, BackendFeatureEnumScan.Result backendToggles, FrontendConstantScan.Result frontendConstants,
        FrontendToggleEnumScan.Result frontendToggles, AdminPageScan.Result adminPage, FeatureI18nScan.Result featureTexts,
        ExtractedConfigurationDefaults configurationDefaults, ComposeFileScan.Result composeFiles, UsageEvidenceScan.Result usageEvidence) {
}
