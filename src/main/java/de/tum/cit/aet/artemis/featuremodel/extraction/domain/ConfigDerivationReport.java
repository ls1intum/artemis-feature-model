package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;

/**
 * Configuration-derivation outcome of one model assembly, persisted as {@code model/config-derivation.json} and
 * rendered as a report section. It lists, per functional member, every configuration key the precedence merge
 * considered — confirmed or added by the manifest, derived from guarded Artemis structure, skipped as a tunable, or
 * rejected by a manifest exclude — together with its evidence.
 *
 * @param members per-member key resolutions sorted by feature id.
 */
public record ConfigDerivationReport(List<MemberConfigDerivation> members) {

    /** Decision of a key a manifest {@code configuration} include entry confirms or adds; emitted first, in entry order. */
    public static final String DECISION_CONFIRMED = "confirmed";

    /** Decision of a derived deployment input without a declaration; emitted after the confirmed keys. */
    public static final String DECISION_DERIVED = "derived";

    /** Decision of a derived candidate classified as a tunable; listed, never emitted. */
    public static final String DECISION_SKIPPED = "skipped";

    /** Decision of a key a manifest {@code configuration} exclude entry rejects; never emitted. */
    public static final String DECISION_REJECTED = "rejected";

    /** Origin of a key declared by a manifest {@code features[].configuration} entry. */
    public static final String ORIGIN_MANIFEST = "manifest";

    /** Origin of a key derived from guarded injection sites, property prefixes, or the enabled-key namespace. */
    public static final String ORIGIN_DERIVED = "derived";

    /** Normalizes the member list to an immutable copy. */
    public ConfigDerivationReport {
        members = members == null ? List.of() : List.copyOf(members);
    }

    /**
     * Key resolutions of one functional member.
     *
     * @param featureId feature id of the member.
     * @param candidateId namespaced extraction candidate id of the member.
     * @param keys key resolutions: emitted keys in emission order, then skipped keys sorted by key, then rejected
     *            keys in manifest order.
     */
    public record MemberConfigDerivation(String featureId, String candidateId, List<ConfigKeyResolution> keys) {

        /** Normalizes the key list to an immutable copy. */
        public MemberConfigDerivation {
            keys = keys == null ? List.of() : List.copyOf(keys);
        }
    }

    /**
     * Resolution of one configuration key of one member.
     *
     * @param key dotted configuration key.
     * @param origin what declared or produced the key, one of the {@code ORIGIN_*} constants.
     * @param decision how the precedence merge resolved the key, one of the {@code DECISION_*} constants.
     * @param secret whether the emitted or classified value is a secret.
     * @param evidence derivation evidence with {@code usage-config-} kinds; empty for keys derivation never saw.
     * @param detail optional human-readable note, for example a redundancy or classification remark, or null.
     */
    public record ConfigKeyResolution(String key, String origin, String decision, boolean secret, List<EvidenceItem> evidence, String detail) {

        /** Normalizes the evidence list to an immutable copy. */
        public ConfigKeyResolution {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }
}
