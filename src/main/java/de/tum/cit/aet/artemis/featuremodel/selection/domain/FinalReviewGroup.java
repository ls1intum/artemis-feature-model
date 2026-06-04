package de.tum.cit.aet.artemis.featuremodel.selection.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinalReviewGroup(String id, String title, int order, List<String> featureIds) {

    /**
     * Creates a final review group and normalizes nullable feature ids to an immutable empty list.
     *
     * @param id stable review group id.
     * @param title display title.
     * @param order review display order.
     * @param featureIds feature ids summarized in this group.
     */
    public FinalReviewGroup {
        featureIds = featureIds == null ? List.of() : List.copyOf(featureIds);
    }
}
