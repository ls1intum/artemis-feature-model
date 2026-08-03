package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.RelationCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;

/** Stateless facade that coordinates cohesive candidate-family assemblers for one extraction invocation. */
final class CandidateAssembler {

    private final ModuleCandidateAssembler moduleAssembler = new ModuleCandidateAssembler();

    private final RelationCandidateAssembler relationAssembler = new RelationCandidateAssembler();

    private final RuntimeToggleCandidateAssembler runtimeToggleAssembler = new RuntimeToggleCandidateAssembler();

    private final DeploymentCandidateAssembler deploymentAssembler = new DeploymentCandidateAssembler();

    private final ConfigKeyCandidateAssembler configKeyAssembler = new ConfigKeyCandidateAssembler();

    /**
     * Assembly result over all scans.
     *
     * @param candidates feature candidates sorted by id.
     * @param evidence evidence items sorted by candidate id, file, line, kind, and symbol.
     * @param relationCandidates relation candidates sorted by id.
     * @param items structural report items produced during assembly.
     */
    record Result(List<FeatureCandidate> candidates, List<EvidenceItem> evidence, List<RelationCandidate> relationCandidates, List<ReportItem> items) {
    }

    /**
     * Assembles all candidate families without retaining invocation data on the facade or its collaborators.
     *
     * @param input immutable scanner facts for one invocation.
     * @return assembly result with established deterministic ordering.
     */
    Result assemble(CandidateAssemblyInput input) {
        CandidateAssemblyContext context = new CandidateAssemblyContext();
        ModuleCandidateAssembler.Result modules = moduleAssembler.assemble(input, context);
        List<RelationCandidate> relations = relationAssembler.assemble(input, modules, context);

        List<FeatureCandidate> candidates = new ArrayList<>(modules.candidates());
        candidates.addAll(runtimeToggleAssembler.assemble(input, context));
        candidates.addAll(deploymentAssembler.assemble(input, context));
        candidates.addAll(configKeyAssembler.assemble(input, context));
        candidates.sort(Comparator.comparing(FeatureCandidate::id));

        return new Result(List.copyOf(candidates), context.sortedEvidence(), relations, context.items());
    }
}
