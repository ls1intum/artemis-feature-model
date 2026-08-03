package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigurationDefault;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;

/** Assembles standalone configuration-key candidates for enabled-property constants. */
final class ConfigKeyCandidateAssembler {

    private static final String PROPERTY_CONSTANT_SUFFIX = ArtemisSourceConventions.Naming.ENABLED_PROPERTY_CONSTANT_SUFFIX;

    /**
     * Assembles configuration-key candidates in backend declaration order.
     *
     * @param input complete candidate-assembly input.
     * @param context invocation-local evidence context.
     * @return configuration-key candidates.
     */
    List<FeatureCandidate> assemble(CandidateAssemblyInput input, CandidateAssemblyContext context) {
        List<FeatureCandidate> candidates = new ArrayList<>();
        for (BackendConstantScan.ScannedConstant constant : input.backendConstants().constants()) {
            if (!constant.name().endsWith(PROPERTY_CONSTANT_SUFFIX)) {
                continue;
            }
            String candidateId = FeatureCandidate.NAMESPACE_CONFIG_KEY + constant.value();
            context.addEvidence(candidateId, EvidenceItem.KIND_BACKEND_CONSTANT, input.backendConstants().file(), constant.line(), constant.name(), null);
            ExtractedConfigurationDefault occurrence = input.configurationDefaults().preferredOccurrence(constant.value());
            if (occurrence != null) {
                context.addEvidence(candidateId, EvidenceItem.KIND_YAML_DEFAULT, occurrence.file(), occurrence.line(), constant.value(),
                        String.valueOf(occurrence.value()));
            }
            Object defaultValue = occurrence == null ? null : occurrence.value();
            candidates.add(new FeatureCandidate(candidateId, FeatureCandidate.KIND_CONFIG_KEY, null, constant.javadoc(), null, constant.value(), defaultValue,
                    constant.name(), null, null, null, null, null, null));
        }
        return candidates;
    }
}
