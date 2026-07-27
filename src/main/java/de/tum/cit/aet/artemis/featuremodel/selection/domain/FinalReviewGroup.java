package de.tum.cit.aet.artemis.featuremodel.selection.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One group of the final review summary. The authored workflow resource declares only {@code groupNodeId} plus an
 * optional title and order; the serve-time enrichment derives the served {@code id}, missing titles and orders, and
 * the member {@code featureIds} from the referenced group node of the active feature model.
 *
 * @param id served review group id; derived from {@code groupNodeId} at serve time.
 * @param groupNodeId id of the feature model group node whose children this group summarizes.
 * @param title display title; defaults to the group node name when not authored.
 * @param order review display order; defaults to the authored position when not authored.
 * @param featureIds member feature ids; derived from the group node children at serve time.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinalReviewGroup(String id, String groupNodeId, String title, int order, List<String> featureIds) {

    /**
     * Creates a final review group and normalizes nullable feature ids to an immutable empty list.
     *
     * @param id served review group id, or null in the authored resource.
     * @param groupNodeId referenced feature model group node id.
     * @param title display title, or null in the authored resource.
     * @param order review display order, or 0 when not authored.
     * @param featureIds feature ids summarized in this group, or null in the authored resource.
     */
    public FinalReviewGroup {
        featureIds = featureIds == null ? List.of() : List.copyOf(featureIds);
    }
}
