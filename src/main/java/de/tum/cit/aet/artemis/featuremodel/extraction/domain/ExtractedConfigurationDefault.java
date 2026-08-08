package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/**
 * One occurrence of a configuration default discovered in the pinned checkout.
 *
 * @param file checkout-relative path of the YAML file.
 * @param line 1-based line of the key.
 * @param value parsed scalar value; booleans and integers are typed, everything else stays a string.
 */
public record ExtractedConfigurationDefault(String file, Integer line, Object value) {
}
