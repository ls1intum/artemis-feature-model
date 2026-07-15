package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.List;

/**
 * Container for the runtime checks serialized into {@code metadata/runtime-checks.json}. Carries the generation mode,
 * an overall status derived from the individual checks, and the ordered check list.
 *
 * @param mode generation mode; only {@code DEMO} in this phase.
 * @param overallStatus {@link RuntimeCheck#STATUS_PASS} unless a check failed, then {@link RuntimeCheck#STATUS_FAIL}.
 * @param checks individual runtime checks in deterministic order.
 */
public record RuntimeChecksReport(String mode, String overallStatus, List<RuntimeCheck> checks) {

    /**
     * Normalizes the check list to an immutable list.
     *
     * @param mode generation mode.
     * @param overallStatus overall status.
     * @param checks individual runtime checks.
     */
    public RuntimeChecksReport {
        checks = checks == null ? List.of() : List.copyOf(checks);
    }
}
