package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;

/**
 * Resolved admin-owned environment values of a remote-ansible generation run. Every input is resolved to either its
 * provided value or its deterministic {@code REPLACE_ME_*} placeholder, so a request without environment values yields
 * a structurally identical placeholder package. The vault server name may be derived from the target name; the target
 * group is the inventory-group form of the target name.
 *
 * @param targetGroup inventory group name of the deployment target.
 * @param inputs resolved inputs in declaration order.
 */
public record RemoteEnvironmentValues(String targetGroup, List<InputValue> inputs) {

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

    /**
     * Remote environment inputs with their request names and placeholders, in declaration order.
     */
    public enum Input {

        /** Target (test-server) name. */
        TARGET_NAME("targetName", "REPLACE_ME_TARGET_NAME"),
        /** Public server hostname; also the inventory host line. */
        SERVER_HOSTNAME("serverHostname", "REPLACE_ME_SERVER_HOSTNAME"),
        /** Operator name. */
        OPERATOR_NAME("operatorName", "REPLACE_ME_OPERATOR_NAME"),
        /** Operator admin name. */
        OPERATOR_ADMIN_NAME("operatorAdminName", "REPLACE_ME_OPERATOR_ADMIN_NAME"),
        /** Contact email address. */
        EMAIL("email", "REPLACE_ME_CONTACT_EMAIL"),
        /** TLS certificate path. */
        CERT_PATH("certPath", "REPLACE_ME_TLS_CERTIFICATE_PATH"),
        /** TLS certificate key path. */
        CERT_KEY_PATH("certKeyPath", "REPLACE_ME_TLS_CERTIFICATE_KEY_PATH"),
        /** Vault server name; derived from the target name when absent. */
        VAULT_SERVER_NAME("vaultServerName", "REPLACE_ME_VAULT_SERVER_NAME");

        private final String inputName;

        private final String placeholder;

        Input(String inputName, String placeholder) {
            this.inputName = inputName;
            this.placeholder = placeholder;
        }

        /**
         * Returns the request-level input name.
         *
         * @return input name.
         */
        public String inputName() {
            return inputName;
        }

        /**
         * Finds an input by its request-level name.
         *
         * @param inputName input name.
         * @return matching input, or {@code null} if the name is unknown.
         */
        public static Input byName(String inputName) {
            for (Input input : values()) {
                if (input.inputName.equals(inputName)) {
                    return input;
                }
            }
            return null;
        }
    }

    /**
     * One resolved environment input.
     *
     * @param input input.
     * @param value resolved value: the provided value or the input's placeholder.
     * @param provided whether the value was provided (directly or derived) instead of a placeholder.
     */
    public record InputValue(Input input, String value, boolean provided) {
    }

    /**
     * Resolves environment values from nullable raw inputs, applying placeholders, deriving the vault server name
     * from the target name when absent, and deriving the inventory group name.
     *
     * @param rawInputs raw input values by input; absent or blank entries resolve to placeholders.
     * @return resolved environment values.
     * @throws ArtifactGenerationException if a provided value cannot be rendered safely.
     */
    public static RemoteEnvironmentValues resolve(Map<Input, String> rawInputs) {
        String targetName = orEmpty(rawInputs.get(Input.TARGET_NAME));
        List<InputValue> inputs = new ArrayList<>();
        for (Input input : Input.values()) {
            String rawValue = orEmpty(rawInputs.get(input));
            if (input == Input.VAULT_SERVER_NAME && rawValue.isEmpty()) {
                rawValue = targetName;
            }
            boolean provided = !rawValue.isEmpty();
            if (provided) {
                validate(input, rawValue);
            }
            inputs.add(new InputValue(input, provided ? rawValue : input.placeholder, provided));
        }
        return new RemoteEnvironmentValues(targetGroupFor(targetName), List.copyOf(inputs));
    }

    /**
     * Resolves the all-placeholder values of a request without an environment component.
     *
     * @return placeholder environment values.
     */
    public static RemoteEnvironmentValues placeholders() {
        return resolve(Map.of());
    }

    /**
     * Returns the resolved value of an input.
     *
     * @param input input.
     * @return resolved value.
     */
    public String valueOf(Input input) {
        return inputs.get(input.ordinal()).value();
    }

    /**
     * Validates a provided value against the syntaxes it is rendered into. Every value is rendered on one line and
     * must not carry Jinja delimiters, because Ansible templates inventory values at run time; the hostname is also
     * an INI host token, and the target and vault server names enter Jinja single-quoted vault paths.
     *
     * @param input input.
     * @param value provided value.
     * @throws ArtifactGenerationException if the value cannot be rendered safely.
     */
    private static void validate(Input input, String value) {
        String inputName = input.inputName();
        if (CONTROL_CHARACTER_PATTERN.matcher(value).find()) {
            throw ArtifactGenerationException.invalidRemoteEnvironmentValue(inputName, "it must be a single line without control characters");
        }
        if (JINJA_DELIMITER_PATTERN.matcher(value).find()) {
            throw ArtifactGenerationException.invalidRemoteEnvironmentValue(inputName, "it must not contain Jinja delimiters such as '{{' or '{%'");
        }
        if (input == Input.SERVER_HOSTNAME && !HOSTNAME_PATTERN.matcher(value).matches()) {
            throw ArtifactGenerationException.invalidRemoteEnvironmentValue(inputName, "it must be a hostname or address (letters, digits, '.', ':', '_', '-')");
        }
        boolean nameInput = input == Input.TARGET_NAME || input == Input.VAULT_SERVER_NAME;
        if (nameInput && !NAME_PATTERN.matcher(value).matches()) {
            throw ArtifactGenerationException.invalidRemoteEnvironmentValue(inputName, "it must be a name (letters, digits, '.', '_', '-')");
        }
    }

    /**
     * Derives the inventory group name of a target name: lowercase with every character outside {@code [a-z0-9_]}
     * removed, matching Ansible group naming, and disambiguated from the reserved wired groups.
     *
     * @param targetName target name, empty for the default group.
     * @return inventory group name.
     */
    private static String targetGroupFor(String targetName) {
        String group = targetName.toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (group.isEmpty()) {
            return DEFAULT_TARGET_GROUP;
        }
        boolean reserved = RESERVED_GROUP.equals(group) || group.startsWith(RESERVED_GROUP_PREFIX);
        return reserved ? group + TARGET_GROUP_SUFFIX : group;
    }

    /**
     * Normalizes a nullable string to a stripped string.
     *
     * @param value nullable string.
     * @return the stripped string, or an empty string.
     */
    private static String orEmpty(String value) {
        return value == null ? "" : value.strip();
    }
}
