package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.List;

/**
 * Result of statically validating a generated configuration overlay against the Artemis configuration key catalog.
 * Serialized into {@code metadata/static-config-validation.json} so both a human reader and a CI gate can consume the
 * same verdict without booting Artemis.
 *
 * @param overallStatus {@link #STATUS_PASS} when there are no findings, otherwise {@link #STATUS_FAIL}.
 * @param catalogVersion version of the catalog the overlay was validated against.
 * @param verifiedAgainstArtemisCommit abbreviated Artemis commit the catalog keys were verified against.
 * @param checkedEntryCount number of overlay entries that were checked.
 * @param findings unknown-key and type-mismatch findings in overlay order, empty when the validation passed.
 */
public record StaticConfigValidationReport(String overallStatus, String catalogVersion, String verifiedAgainstArtemisCommit, int checkedEntryCount,
        List<StaticConfigFinding> findings) {

    /** Every overlay entry is a verified Artemis key with an acceptable value type. */
    public static final String STATUS_PASS = "PASS";

    /** At least one overlay entry is unknown or has an unacceptable value type. */
    public static final String STATUS_FAIL = "FAIL";

    /**
     * Normalizes the finding list to an immutable list.
     *
     * @param overallStatus overall validation status.
     * @param catalogVersion catalog version.
     * @param verifiedAgainstArtemisCommit abbreviated Artemis commit.
     * @param checkedEntryCount number of checked overlay entries.
     * @param findings validation findings.
     */
    public StaticConfigValidationReport {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
