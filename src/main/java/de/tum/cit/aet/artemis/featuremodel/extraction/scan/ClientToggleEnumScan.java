package de.tum.cit.aet.artemis.featuremodel.extraction.scan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceLocator;

/**
 * Scans the client {@code FeatureToggle} enum mirroring the server {@code Feature} enum. The service file is
 * located by name so the scan survives client restructurings; the accepted member shape is
 * {@code Name = 'Name',} inside the enum block.
 */
class ClientToggleEnumScan {

    private static final Pattern ENUM_START_PATTERN = Pattern
            .compile("^export enum " + Pattern.quote(ArtemisSourceConventions.Symbols.CLIENT_FEATURE_ENUM) + "\\s*\\{\\s*$");

    private static final Pattern MEMBER_PATTERN = Pattern.compile("^\\s*(\\w+)\\s*=\\s*'([^']*)',?\\s*$");

    private final ArtemisSourceLocator sourceLocator = new ArtemisSourceLocator();

    /**
     * One scanned client enum member.
     *
     * @param name enum member name.
     * @param value literal string value of the member.
     * @param line 1-based declaration line.
     */
    record ScannedToggleMember(String name, String value, Integer line) {
    }

    /**
     * Scan result of the client toggle enum.
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
     * Scans the client toggle enum of the given checkout.
     *
     * @param source Artemis source repository.
     * @return scanned enum members.
     * @throws IOException if the service file cannot be read.
     * @throws IllegalArgumentException if the service file cannot be located or contains no enum block.
     */
    Result scan(ArtemisSourceRepository source) throws IOException {
        String file = sourceLocator.locate(source, ArtemisSourceConventions.Files.CLIENT_TOGGLE_SERVICE,
                "enum " + ArtemisSourceConventions.Symbols.CLIENT_FEATURE_ENUM,
                content -> content.contains("enum " + ArtemisSourceConventions.Symbols.CLIENT_FEATURE_ENUM));
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
        throw new IllegalArgumentException(
                "File " + file + " does not declare an export enum " + ArtemisSourceConventions.Symbols.CLIENT_FEATURE_ENUM + " block.");
    }
}
