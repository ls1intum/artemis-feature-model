package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.List;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;

/**
 * Resolved admin-owned environment values of a remote-ansible generation run. Every input is resolved to either its
 * provided value or a deterministic {@code REPLACE_ME_*} placeholder, so a request without environment values yields
 * a structurally identical placeholder package. The vault server name may be derived from the target name; the target
 * group is the inventory-group form of the target name.
 *
 * @param targetGroup inventory group name of the deployment target.
 * @param inputs resolved inputs in declaration order.
 */
public record RemoteEnvironmentValues(String targetGroup, List<InputValue> inputs) {

    /** Input name of the target (test-server) name. */
    public static final String INPUT_TARGET_NAME = "targetName";

    /** Input name of the server hostname. */
    public static final String INPUT_SERVER_HOSTNAME = "serverHostname";

    /** Input name of the operator name. */
    public static final String INPUT_OPERATOR_NAME = "operatorName";

    /** Input name of the operator admin name. */
    public static final String INPUT_OPERATOR_ADMIN_NAME = "operatorAdminName";

    /** Input name of the contact email address. */
    public static final String INPUT_EMAIL = "email";

    /** Input name of the TLS certificate path. */
    public static final String INPUT_CERT_PATH = "certPath";

    /** Input name of the TLS certificate key path. */
    public static final String INPUT_CERT_KEY_PATH = "certKeyPath";

    /** Input name of the vault server name; derived from the target name when absent. */
    public static final String INPUT_VAULT_SERVER_NAME = "vaultServerName";

    /** All input names in declaration order. */
    public static final List<String> INPUT_NAMES = List.of(INPUT_TARGET_NAME, INPUT_SERVER_HOSTNAME, INPUT_OPERATOR_NAME, INPUT_OPERATOR_ADMIN_NAME,
            INPUT_EMAIL, INPUT_CERT_PATH, INPUT_CERT_KEY_PATH, INPUT_VAULT_SERVER_NAME);

    /** Inventory group name used when no target name is provided. */
    public static final String DEFAULT_TARGET_GROUP = "artemistarget";

    /** Inventory group every generated target joins; a derived target group must never collide with it. */
    public static final String RESERVED_GROUP = "artemistests";

    /** Prefix of every wired values group; a derived target group must never collide with one of them. */
    public static final String RESERVED_GROUP_PREFIX = "artemistests_";

    /** Suffix that disambiguates a derived target group from a reserved group name. */
    private static final String TARGET_GROUP_SUFFIX = "_target";

    /** Hostname or address token allowed as the inventory host line and inside the server URL. */
    private static final Pattern HOSTNAME_PATTERN = Pattern.compile("[A-Za-z0-9.:_\\-]+");

    /** Name token allowed where a value enters a Jinja single-quoted string or a vault path segment. */
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9._\\-]+");

    /** Jinja delimiters that would make Ansible template a rendered value at run time. */
    private static final Pattern JINJA_DELIMITER_PATTERN = Pattern.compile("\\{\\{|\\{%|\\{#|\\}\\}|%\\}|#\\}");

    /** Control characters (including line breaks) that would break the single-line rendering. */
    private static final Pattern CONTROL_CHARACTER_PATTERN = Pattern.compile("[\\p{Cntrl}]");

    private static final List<String> PLACEHOLDERS = List.of("REPLACE_ME_TARGET_NAME", "REPLACE_ME_SERVER_HOSTNAME", "REPLACE_ME_OPERATOR_NAME",
            "REPLACE_ME_OPERATOR_ADMIN_NAME", "REPLACE_ME_CONTACT_EMAIL", "REPLACE_ME_TLS_CERTIFICATE_PATH", "REPLACE_ME_TLS_CERTIFICATE_KEY_PATH",
            "REPLACE_ME_VAULT_SERVER_NAME");

    /**
     * One resolved environment input.
     *
     * @param input input name.
     * @param value resolved value: the provided value or the input's placeholder.
     * @param provided whether the value was provided (directly or derived) instead of a placeholder.
     */
    public record InputValue(String input, String value, boolean provided) {
    }

    /**
     * Normalizes the input list to an immutable list.
     *
     * @param targetGroup inventory group name.
     * @param inputs resolved inputs.
     */
    public RemoteEnvironmentValues {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }

    /**
     * Resolves environment values from nullable raw inputs, applying placeholders, deriving the vault server name
     * from the target name when absent, and deriving the inventory group name.
     *
     * @param targetName target name, or {@code null}/blank.
     * @param serverHostname server hostname, or {@code null}/blank.
     * @param operatorName operator name, or {@code null}/blank.
     * @param operatorAdminName operator admin name, or {@code null}/blank.
     * @param email contact email, or {@code null}/blank.
     * @param certPath TLS certificate path, or {@code null}/blank.
     * @param certKeyPath TLS certificate key path, or {@code null}/blank.
     * @param vaultServerName vault server name, or {@code null}/blank to derive it from the target name.
     * @return resolved environment values.
     */
    public static RemoteEnvironmentValues resolve(String targetName, String serverHostname, String operatorName, String operatorAdminName, String email,
            String certPath, String certKeyPath, String vaultServerName) {
        String resolvedVaultServerName = isBlank(vaultServerName) ? targetName : vaultServerName;
        List<String> rawValues = List.of(orEmpty(targetName), orEmpty(serverHostname), orEmpty(operatorName), orEmpty(operatorAdminName), orEmpty(email),
                orEmpty(certPath), orEmpty(certKeyPath), orEmpty(resolvedVaultServerName));
        List<InputValue> inputs = new java.util.ArrayList<>();
        for (int index = 0; index < INPUT_NAMES.size(); index++) {
            String input = INPUT_NAMES.get(index);
            String rawValue = rawValues.get(index);
            boolean provided = !rawValue.isBlank();
            if (provided) {
                validate(input, rawValue);
            }
            inputs.add(new InputValue(input, provided ? rawValue : PLACEHOLDERS.get(index), provided));
        }
        return new RemoteEnvironmentValues(targetGroupFor(targetName), List.copyOf(inputs));
    }

    /**
     * Validates a provided value against the syntaxes it is rendered into. Every value is rendered on one line and
     * must not carry Jinja delimiters, because Ansible templates inventory values at run time; the hostname is also
     * an INI host token, and the target and vault server names enter Jinja single-quoted vault paths.
     *
     * @param input input name.
     * @param value provided value.
     * @throws ArtifactGenerationException if the value cannot be rendered safely.
     */
    private static void validate(String input, String value) {
        if (CONTROL_CHARACTER_PATTERN.matcher(value).find()) {
            throw ArtifactGenerationException.invalidRemoteEnvironmentValue(input, "it must be a single line without control characters");
        }
        if (JINJA_DELIMITER_PATTERN.matcher(value).find()) {
            throw ArtifactGenerationException.invalidRemoteEnvironmentValue(input, "it must not contain Jinja delimiters such as '{{' or '{%'");
        }
        if (INPUT_SERVER_HOSTNAME.equals(input) && !HOSTNAME_PATTERN.matcher(value).matches()) {
            throw ArtifactGenerationException.invalidRemoteEnvironmentValue(input, "it must be a hostname or address (letters, digits, '.', ':', '_', '-')");
        }
        boolean nameInput = INPUT_TARGET_NAME.equals(input) || INPUT_VAULT_SERVER_NAME.equals(input);
        if (nameInput && !NAME_PATTERN.matcher(value).matches()) {
            throw ArtifactGenerationException.invalidRemoteEnvironmentValue(input, "it must be a name (letters, digits, '.', '_', '-')");
        }
    }

    /**
     * Escapes a resolved value for use inside a YAML double-quoted scalar.
     *
     * @param value resolved value.
     * @return value with backslashes and double quotes escaped.
     */
    public static String yamlDoubleQuoted(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Resolves the all-placeholder values of a request without an environment component.
     *
     * @return placeholder environment values.
     */
    public static RemoteEnvironmentValues placeholders() {
        return resolve(null, null, null, null, null, null, null, null);
    }

    /**
     * Returns the resolved value of an input.
     *
     * @param input input name.
     * @return resolved value.
     * @throws IllegalArgumentException if the input name is unknown.
     */
    public String valueOf(String input) {
        return inputValue(input).value();
    }

    /**
     * Checks whether an input was provided instead of resolved to a placeholder.
     *
     * @param input input name.
     * @return true when the input was provided or derived.
     */
    public boolean isProvided(String input) {
        return inputValue(input).provided();
    }

    /**
     * Finds a resolved input by name.
     *
     * @param input input name.
     * @return resolved input.
     * @throws IllegalArgumentException if the input name is unknown.
     */
    private InputValue inputValue(String input) {
        for (InputValue value : inputs) {
            if (value.input().equals(input)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown remote environment input '" + input + "'.");
    }

    /**
     * Derives the inventory group name of a target name: lowercase with every character outside {@code [a-z0-9_]}
     * removed, matching Ansible group naming.
     *
     * @param targetName target name, or {@code null}/blank for the default group.
     * @return inventory group name.
     */
    private static String targetGroupFor(String targetName) {
        if (isBlank(targetName)) {
            return DEFAULT_TARGET_GROUP;
        }
        String group = targetName.toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (group.isBlank()) {
            return DEFAULT_TARGET_GROUP;
        }
        boolean reserved = RESERVED_GROUP.equals(group) || group.startsWith(RESERVED_GROUP_PREFIX);
        return reserved ? group + TARGET_GROUP_SUFFIX : group;
    }

    /**
     * Normalizes a nullable string to an empty string.
     *
     * @param value nullable string.
     * @return the string, or an empty string.
     */
    private static String orEmpty(String value) {
        return value == null ? "" : value.strip();
    }

    /**
     * Checks whether a string is null or blank.
     *
     * @param value string to check.
     * @return true for null or blank.
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
