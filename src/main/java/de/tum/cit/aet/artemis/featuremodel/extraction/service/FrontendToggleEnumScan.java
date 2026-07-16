package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;

/**
 * Scans the frontend {@code FeatureToggle} enum mirroring the backend {@code Feature} enum. The service file is
 * located by name so the scan survives frontend restructurings; the accepted member shape is
 * {@code Name = 'Name',} inside the enum block.
 */
class FrontendToggleEnumScan {

    static final String DEFAULT_TOGGLE_SERVICE_PATH = "src/main/webapp/app/foundation/feature-toggle/feature-toggle.service.ts";

    private static final String TOGGLE_SERVICE_FILE_NAME = "feature-toggle.service.ts";

    private static final Pattern ENUM_START_PATTERN = Pattern.compile("^export enum FeatureToggle\\s*\\{\\s*$");

    private static final Pattern MEMBER_PATTERN = Pattern.compile("^\\s*(\\w+)\\s*=\\s*'([^']*)',?\\s*$");

    /**
     * One scanned frontend enum member.
     *
     * @param name enum member name.
     * @param value literal string value of the member.
     * @param line 1-based declaration line.
     */
    record ScannedToggleMember(String name, String value, Integer line) {
    }

    /**
     * Scan result of the frontend toggle enum.
     *
     * @param file checkout-relative path of the scanned file.
     * @param members enum members in declaration order.
     */
    record Result(String file, List<ScannedToggleMember> members) {

        /**
         * Creates an empty result for a failed or skipped scan.
         *
         * @return result without members.
         */
        static Result empty() {
            return new Result(null, List.of());
        }
    }

    /**
     * Scans the frontend toggle enum of the given checkout.
     *
     * @param source Artemis source repository.
     * @return scanned enum members.
     * @throws IOException if the service file cannot be read.
     * @throws IllegalArgumentException if the service file cannot be located or contains no enum block.
     */
    Result scan(ArtemisSourceRepository source) throws IOException {
        String file = locateToggleServiceFile(source);
        List<String> lines = source.readLines(file);
        List<ScannedToggleMember> members = new ArrayList<>();
        boolean insideEnum = false;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!insideEnum) {
                insideEnum = ENUM_START_PATTERN.matcher(line).matches();
                continue;
            }
            if (line.strip().startsWith("}")) {
                return new Result(file, List.copyOf(members));
            }
            Matcher matcher = MEMBER_PATTERN.matcher(line);
            if (matcher.matches()) {
                members.add(new ScannedToggleMember(matcher.group(1), matcher.group(2), index + 1));
            }
        }
        throw new IllegalArgumentException("File " + file + " does not declare an export enum FeatureToggle block.");
    }

    /**
     * Locates the feature toggle service file, preferring the known location and falling back to a name-based search.
     *
     * @param source Artemis source repository.
     * @return checkout-relative path of the service file.
     * @throws IOException if the search fails.
     * @throws IllegalArgumentException if no service file can be found.
     */
    private String locateToggleServiceFile(ArtemisSourceRepository source) throws IOException {
        if (source.fileExists(DEFAULT_TOGGLE_SERVICE_PATH)) {
            return DEFAULT_TOGGLE_SERVICE_PATH;
        }
        List<String> matches = source.findFilesByName("src/main/webapp", TOGGLE_SERVICE_FILE_NAME);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No " + TOGGLE_SERVICE_FILE_NAME + " found under src/main/webapp.");
        }
        return matches.getFirst();
    }
}
