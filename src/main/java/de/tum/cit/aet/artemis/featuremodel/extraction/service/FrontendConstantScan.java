package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceLocator;

/**
 * Scans {@code app.constants.ts} for the frontend module feature and profile constants. The accepted shape is a
 * single-line literal export ({@code export const MODULE_FEATURE_X = 'value';}); the fixture tests pin this shape.
 */
class FrontendConstantScan {

    private static final Pattern CONSTANT_PATTERN = Pattern.compile("^export const (" + Pattern.quote(ArtemisSourceConventions.Symbols.MODULE_FEATURE_PREFIX)
            + "\\w+|" + Pattern.quote(ArtemisSourceConventions.Symbols.PROFILE_CONSTANT_PREFIX) + "\\w+)\\s*=\\s*'([^']*)';\\s*$");

    private final ArtemisSourceLocator sourceLocator = new ArtemisSourceLocator();

    /**
     * One scanned frontend constant.
     *
     * @param name constant name, for example {@code MODULE_FEATURE_PASSKEY}.
     * @param value literal string value.
     * @param line 1-based declaration line.
     */
    record ScannedFrontendConstant(String name, String value, Integer line) {
    }

    /**
     * Scan result of the frontend constants file.
     *
     * @param file checkout-relative path of the scanned file.
     * @param constants scanned constants in declaration order.
     */
    record Result(String file, List<ScannedFrontendConstant> constants) {

        /**
         * Creates an empty result for a failed or skipped scan.
         *
         * @return result without constants.
         */
        static Result empty() {
            return new Result(null, List.of());
        }
    }

    /**
     * Scans the frontend constants of the given checkout.
     *
     * @param source Artemis source repository.
     * @return scanned frontend constants.
     * @throws IOException if the constants file cannot be read.
     * @throws IllegalArgumentException if the constants file cannot be located.
     */
    Result scan(ArtemisSourceRepository source) throws IOException {
        String file = sourceLocator.locate(source, ArtemisSourceConventions.Files.FRONTEND_CONSTANTS);
        List<ScannedFrontendConstant> constants = new ArrayList<>();
        List<String> lines = source.readLines(file);
        for (int index = 0; index < lines.size(); index++) {
            Matcher matcher = CONSTANT_PATTERN.matcher(lines.get(index));
            if (matcher.matches()) {
                constants.add(new ScannedFrontendConstant(matcher.group(1), matcher.group(2), index + 1));
            }
        }
        return new Result(file, List.copyOf(constants));
    }

}
