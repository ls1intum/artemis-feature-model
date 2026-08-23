package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.List;

/**
 * Catalog of the Artemis configuration keys the generated overlay is allowed to contain, together with the value type
 * each key accepts. The catalog is a curated classpath resource derived from a parameter alignment audit and
 * re-checked read-only against a pinned Artemis commit; it is the source of truth for the static configuration
 * validation and must be refreshed when Artemis configuration keys change.
 *
 * @param catalogVersion version of the catalog resource itself.
 * @param verifiedAgainstArtemisCommit abbreviated Artemis commit the keys were verified against.
 * @param source human-readable provenance note for the catalog content.
 * @param keys verified configuration keys with their accepted value types.
 */
public record ArtemisConfigKeyCatalog(String catalogVersion, String verifiedAgainstArtemisCommit, String source, List<CatalogKey> keys) {

    /** Accepted type for keys whose value must be a YAML boolean scalar. */
    public static final String TYPE_BOOLEAN = "boolean";

    /** Accepted type for keys whose value must be a string scalar. */
    public static final String TYPE_STRING = "string";

    /** Accepted type for keys whose value must be an absolute http or https URL. */
    public static final String TYPE_URL = "url";

    /**
     * Normalizes the key list to an immutable list.
     *
     * @param catalogVersion version of the catalog resource.
     * @param verifiedAgainstArtemisCommit abbreviated Artemis commit the keys were verified against.
     * @param source provenance note.
     * @param keys verified configuration keys.
     */
    public ArtemisConfigKeyCatalog {
        keys = keys == null ? List.of() : List.copyOf(keys);
    }

    /**
     * A single verified configuration key.
     *
     * @param key dotted Artemis configuration key.
     * @param type accepted value type, one of {@link #TYPE_BOOLEAN}, {@link #TYPE_STRING}, or {@link #TYPE_URL}.
     */
    public record CatalogKey(String key, String type) {
    }
}
