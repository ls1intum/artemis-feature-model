package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;

/**
 * Resolved target identity of a remote-ansible generation run. The target name is the only admin-owned value the
 * generation still consumes: every other environment value is emitted as a {@code lookup('ansible.builtin.env', …)}
 * expression, so the package bakes no environment value. The target group is the inventory-group form of the target
 * name; a request without a target name resolves to the default group.
 *
 * @param targetGroup inventory group name of the deployment target.
 */
public record RemoteEnvironmentValues(String targetGroup) {

    /** Inventory group name used when no target name is provided. */
    public static final String DEFAULT_TARGET_GROUP = "artemistarget";

    /** Inventory group every generated target joins; a derived target group must never collide with it. */
    public static final String RESERVED_GROUP = "artemistests";

    /** Prefix of every wired values group; a derived target group must never collide with one of them. */
    public static final String RESERVED_GROUP_PREFIX = "artemistests_";

    /** Suffix that disambiguates a derived target group from a reserved group name. */
    private static final String TARGET_GROUP_SUFFIX = "_target";

    /** Name token allowed as a target name. */
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9._\\-]+");

    /**
     * Resolves the target identity from a nullable raw target name.
     *
     * @param targetName raw target name; absent or blank resolves to the default target group.
     * @return resolved target identity.
     * @throws ArtifactGenerationException if a provided target name is not a safe name token.
     */
    public static RemoteEnvironmentValues resolve(String targetName) {
        String stripped = targetName == null ? "" : targetName.strip();
        if (!stripped.isEmpty() && !NAME_PATTERN.matcher(stripped).matches()) {
            throw ArtifactGenerationException.invalidRemoteEnvironmentValue("targetName", "it must be a name (letters, digits, '.', '_', '-')");
        }
        return new RemoteEnvironmentValues(targetGroupFor(stripped));
    }

    /**
     * Resolves the default target identity of a request without an environment component.
     *
     * @return default target identity.
     */
    public static RemoteEnvironmentValues defaultTarget() {
        return resolve(null);
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
}
