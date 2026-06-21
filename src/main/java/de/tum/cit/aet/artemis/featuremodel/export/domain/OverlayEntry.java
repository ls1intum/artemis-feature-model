package de.tum.cit.aet.artemis.featuremodel.export.domain;

/**
 * A single resolved configuration entry to write into the generated YAML overlay.
 *
 * @param path dotted configuration path, for example {@code artemis.iris.url}.
 * @param value typed value to write (Boolean, Long, Double, or String). Environment references are carried as the
 *            {@code ${NAME}} placeholder string.
 */
public record OverlayEntry(String path, Object value) {
}
