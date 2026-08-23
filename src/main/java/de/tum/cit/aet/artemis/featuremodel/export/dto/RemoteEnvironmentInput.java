package de.tum.cit.aet.artemis.featuremodel.export.dto;

import java.util.EnumMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.featuremodel.export.domain.RemoteEnvironmentValues;

/**
 * Optional admin-owned environment values of a remote-ansible generation request. Every component is null-tolerant:
 * an absent component (or absent fields) yields a placeholder package whose readiness records the pending inputs.
 * Supplying the component on a non-remote deployment mode is rejected with a controlled bad request, never silently
 * ignored.
 *
 * @param targetName target (test-server) name, for example {@code artemis-local}.
 * @param serverHostname public hostname of the server.
 * @param operatorName operator name shown by Artemis; mandatory in the rendered configuration.
 * @param operatorAdminName operator admin name shown by Artemis; mandatory in the rendered configuration.
 * @param email admin contact email address.
 * @param certPath TLS certificate path on the target host.
 * @param certKeyPath TLS certificate key path on the target host.
 * @param vaultServerName vault server name; derived from {@code targetName} when absent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteEnvironmentInput(String targetName, String serverHostname, String operatorName, String operatorAdminName, String email, String certPath,
        String certKeyPath, String vaultServerName) {

    /**
     * Resolves this input to environment values with placeholders for absent fields.
     *
     * @return resolved environment values.
     */
    public RemoteEnvironmentValues resolve() {
        Map<RemoteEnvironmentValues.Input, String> rawInputs = new EnumMap<>(RemoteEnvironmentValues.Input.class);
        rawInputs.put(RemoteEnvironmentValues.Input.TARGET_NAME, targetName);
        rawInputs.put(RemoteEnvironmentValues.Input.SERVER_HOSTNAME, serverHostname);
        rawInputs.put(RemoteEnvironmentValues.Input.OPERATOR_NAME, operatorName);
        rawInputs.put(RemoteEnvironmentValues.Input.OPERATOR_ADMIN_NAME, operatorAdminName);
        rawInputs.put(RemoteEnvironmentValues.Input.EMAIL, email);
        rawInputs.put(RemoteEnvironmentValues.Input.CERT_PATH, certPath);
        rawInputs.put(RemoteEnvironmentValues.Input.CERT_KEY_PATH, certKeyPath);
        rawInputs.put(RemoteEnvironmentValues.Input.VAULT_SERVER_NAME, vaultServerName);
        return RemoteEnvironmentValues.resolve(rawInputs);
    }
}
