package de.tum.cit.aet.artemis.featuremodel.export.domain;

/**
 * Record of an artifact mapping that was not written into the overlay, with the reason it was skipped.
 *
 * @param featureId feature that owns the mapping.
 * @param targetPath configuration path that would have been written.
 * @param reason human-readable reason the value was omitted.
 */
public record OmittedMapping(String featureId, String targetPath, String reason) {
}
