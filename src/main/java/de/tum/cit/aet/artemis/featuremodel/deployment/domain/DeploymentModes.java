package de.tum.cit.aet.artemis.featuremodel.deployment.domain;

import java.util.List;

/**
 * Stable deployment-mode identifiers for the export-time deployment-mode axis. A deployment mode describes how the
 * generated artifacts are materialized and consumed (for example as a local Docker runtime package); it is orthogonal
 * to the functional selection and to the deployment profile, never a node in the functional feature model, and is
 * exposed on the wire as a plain string, not an enum.
 */
public final class DeploymentModes {

    /** Local Docker runtime package (Phase 6 Layer 1); the default mode and today's behavior. */
    public static final String LOCAL_DOCKER = "local-docker";

    /** Configuration-only IDE development setup: the overlay plus a generated IntelliJ run configuration. */
    public static final String DEV_IDE = "dev-ide";

    /** Admin-consumable Ansible deployment package for a remote server, generated from the Ansible binding catalog. */
    public static final String REMOTE_ANSIBLE = "remote-ansible";

    private static final List<String> KNOWN_MODE_IDS = List.of(LOCAL_DOCKER, DEV_IDE, REMOTE_ANSIBLE);

    private DeploymentModes() {
    }

    /**
     * Checks whether a mode id is a known deployment mode.
     *
     * @param modeId mode id to check, may be {@code null}.
     * @return true if the id names a known deployment mode.
     */
    public static boolean isKnown(String modeId) {
        return modeId != null && KNOWN_MODE_IDS.contains(modeId);
    }
}
