package de.tum.cit.aet.artemis.featuremodel.extraction.scan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.RelationCandidate;

/** Builds composite-condition relation candidates and condition-usage evidence from resolved module joins. */
final class RelationCandidateAssembler {

    private static final String RELATION_ID_PREFIX = "relation:";

    /**
     * Assembles relation candidates and attaches conditional usage evidence.
     *
     * @param input complete candidate-assembly input.
     * @param modules resolved module family facts.
     * @param context invocation-local evidence context.
     * @return relation candidates sorted by id.
     */
    List<RelationCandidate> assemble(CandidateAssemblyInput input, ModuleCandidateAssembler.Result modules, CandidateAssemblyContext context) {
        List<RelationCandidate> relations = emitRelations(input, modules, context);
        attachConditionalUsageEvidence(input, modules, context);
        return relations;
    }

    /** Emits composite conditions that resolve at least two module members. */
    private List<RelationCandidate> emitRelations(CandidateAssemblyInput input, ModuleCandidateAssembler.Result modules, CandidateAssemblyContext context) {
        List<RelationCandidate> relations = new ArrayList<>();
        for (ConditionClassScan.ScannedCondition condition : input.conditions().conditions()) {
            Set<String> keys = modules.conditionPropertyKeys().getOrDefault(condition.className(), Set.of());
            if (keys.size() < 2) {
                continue;
            }
            List<String> memberIds = new ArrayList<>();
            for (String key : keys) {
                String memberId = modules.candidateIdForConfigKey(key);
                if (memberId != null && !memberIds.contains(memberId)) {
                    memberIds.add(memberId);
                }
            }
            if (memberIds.size() < 2) {
                continue;
            }
            memberIds.sort(Comparator.naturalOrder());
            String sourceId = modules.candidateIdForCondition(condition.className());
            boolean directed = sourceId != null;
            List<String> requiredIds = memberIds.stream().filter(memberId -> !memberId.equals(sourceId)).toList();
            String detail = directed
                    ? "Condition " + condition.className() + " enables " + sourceId + " only when " + String.join(", ", requiredIds) + " is enabled as well."
                    : "Composite condition " + condition.className() + " requires all of " + String.join(", ", requiredIds)
                            + "; the direction is a curation decision.";
            String relationId = RELATION_ID_PREFIX + condition.className();
            relations.add(new RelationCandidate(relationId, RelationCandidate.TYPE_REQUIRES, sourceId, memberIds, directed, condition.className(),
                    RelationCandidate.STATUS_CANDIDATE, detail));
            context.addEvidence(relationId, EvidenceItem.KIND_CONDITION_CLASS, condition.file(), condition.line(), condition.className(), null);
        }
        relations.sort(Comparator.comparing(RelationCandidate::id));
        return List.copyOf(relations);
    }

    /** Attaches each conditional usage site to its owning or composite-member modules. */
    private void attachConditionalUsageEvidence(CandidateAssemblyInput input, ModuleCandidateAssembler.Result modules, CandidateAssemblyContext context) {
        Map<String, List<String>> memberCandidateIdsByCondition = new LinkedHashMap<>();
        for (ConditionClassScan.ScannedCondition condition : input.conditions().conditions()) {
            List<String> memberIds = new ArrayList<>();
            String ownCandidateId = modules.candidateIdForCondition(condition.className());
            if (ownCandidateId != null) {
                memberIds.add(ownCandidateId);
            }
            else {
                for (String key : modules.conditionPropertyKeys().getOrDefault(condition.className(), Set.of())) {
                    String memberId = modules.candidateIdForConfigKey(key);
                    if (memberId != null && !memberIds.contains(memberId)) {
                        memberIds.add(memberId);
                    }
                }
            }
            memberCandidateIdsByCondition.put(condition.className(), memberIds);
        }
        for (UsageEvidenceScan.UsageSite site : input.usageEvidence().conditionalSites()) {
            for (String candidateId : memberCandidateIdsByCondition.getOrDefault(site.symbol(), List.of())) {
                String detail = memberCandidateIdsByCondition.get(site.symbol()).size() > 1 ? "via composite condition " + site.symbol() : null;
                context.addEvidence(candidateId, EvidenceItem.KIND_USAGE_CONDITIONAL, site.file(), site.line(), site.symbol(), detail);
            }
        }
    }
}
