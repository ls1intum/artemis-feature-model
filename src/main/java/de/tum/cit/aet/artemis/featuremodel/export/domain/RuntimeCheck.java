package de.tum.cit.aet.artemis.featuremodel.export.domain;

/**
 * A single runtime-oriented check computed at generation time and serialized into {@code metadata/runtime-checks.json}.
 * Checks describe safety and wiring invariants of the generated package (for example, no {@code env:} leaks in the
 * overlay) so a user can trust the package without re-deriving these facts by hand.
 *
 * @param id stable check id.
 * @param description what the check verifies.
 * @param status check outcome, one of {@link #STATUS_PASS}, {@link #STATUS_FAIL}, or {@link #STATUS_INFO}.
 * @param detail human-readable detail about the outcome.
 */
public record RuntimeCheck(String id, String description, String status, String detail) {

    /** The checked invariant holds. */
    public static final String STATUS_PASS = "PASS";

    /** The checked invariant does not hold. */
    public static final String STATUS_FAIL = "FAIL";

    /** Informational note that does not affect the overall status. */
    public static final String STATUS_INFO = "INFO";
}
