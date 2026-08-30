package de.tum.cit.aet.artemis.featuremodel.export.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.featuremodel.export.domain.RemoteEnvironmentValues;

/**
 * Optional target identity of a remote-ansible generation request. The target name is the only value generation still
 * consumes: it names the inventory target group and routes a published package. Every other environment value rides
 * the environment channel as a {@code lookup('ansible.builtin.env', …)} expression. The component is null-tolerant —
 * an absent component (or absent target name) yields the default target group. Supplying the component on a
 * non-remote deployment mode is rejected with a controlled bad request, never silently ignored.
 *
 * @param targetName target (test-server) name, for example {@code artemis-local}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteEnvironmentInput(String targetName) {

    /**
     * Resolves this input to the target identity, falling back to the default target group.
     *
     * @return resolved target identity.
     */
    public RemoteEnvironmentValues resolve() {
        return RemoteEnvironmentValues.resolve(targetName);
    }
}
