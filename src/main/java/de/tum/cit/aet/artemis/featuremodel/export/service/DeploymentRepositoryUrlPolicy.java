package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.List;
import java.util.Locale;

import de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentRepositoryPublishException;

/**
 * Resolves the effective deployment repository URL of a publish and checks it against the allowed prefixes. A
 * requested URL reaches JGit's clone and push, so the policy keeps the instance credentials away from local paths and
 * unrelated hosts: the configured default passes the same checks as a requested URL. Violation messages never repeat
 * the URL, because a rejected URL may carry a credential in its user information.
 */
final class DeploymentRepositoryUrlPolicy {

    private static final String PARENT_SEGMENT = "..";

    private DeploymentRepositoryUrlPolicy() {
    }

    /**
     * Resolves the effective URL: the trimmed requested URL, else the configured default, and checks it.
     *
     * @param requestedUrl repository URL of the publish request, or {@code null}/blank for the default.
     * @param properties deployment repository configuration carrying the default URL and the allowed prefixes.
     * @return effective, trimmed repository URL.
     * @throws DeploymentRepositoryPublishException if neither URL is present or the effective URL violates the policy.
     */
    static String effectiveUrl(String requestedUrl, DeploymentRepositoryProperties properties) {
        String effectiveUrl = hasText(requestedUrl) ? requestedUrl.strip() : properties.repositoryUrl();
        if (!hasText(effectiveUrl)) {
            throw DeploymentRepositoryPublishException.requiresRepositoryUrl();
        }
        effectiveUrl = effectiveUrl.strip();
        String violation = violation(effectiveUrl, properties.allowedRepositoryUrlPrefixes());
        if (violation != null) {
            throw DeploymentRepositoryPublishException.repositoryNotAllowed(violation, properties.allowedRepositoryUrlPrefixes());
        }
        return effectiveUrl;
    }

    /**
     * Names the first policy violation of a trimmed URL, checked in a fixed order.
     *
     * @param url trimmed repository URL.
     * @param allowedPrefixes allowed URL prefixes.
     * @return violation description, or {@code null} when the URL is allowed.
     */
    private static String violation(String url, List<String> allowedPrefixes) {
        if (containsWhitespaceOrControlCharacter(url)) {
            return "it contains whitespace or control characters";
        }
        if (url.indexOf('?') >= 0 || url.indexOf('#') >= 0) {
            return "it contains a query or fragment";
        }
        if (containsParentSegment(url)) {
            return "it contains a '..' path segment";
        }
        if (carriesHttpUserInfo(url)) {
            return "it carries user information; credentials come only from the server environment";
        }
        if (!startsWithAllowedPrefix(url, allowedPrefixes)) {
            return "it does not start with an allowed prefix followed by a repository path";
        }
        return null;
    }

    /**
     * Checks for whitespace, space separators, or control characters anywhere in the URL.
     *
     * @param url trimmed repository URL.
     * @return whether such a character is present.
     */
    private static boolean containsWhitespaceOrControlCharacter(String url) {
        return url.codePoints().anyMatch(codePoint -> Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)
                || Character.isISOControl(codePoint));
    }

    /**
     * Checks for a {@code ..} segment between path separators, including the {@code :} of scp-like SSH URLs.
     *
     * @param url trimmed repository URL.
     * @return whether a parent segment is present.
     */
    private static boolean containsParentSegment(String url) {
        for (String segment : url.split("[/:\\\\]", -1)) {
            if (PARENT_SEGMENT.equals(segment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether an HTTP(S) URL carries user information such as {@code https://token@github.com/...}.
     *
     * @param url trimmed repository URL.
     * @return whether the authority of an HTTP(S) URL contains {@code @}.
     */
    private static boolean carriesHttpUserInfo(String url) {
        String normalized = url.toLowerCase(Locale.ROOT);
        String schemeSeparator = "://";
        if (!normalized.startsWith("http" + schemeSeparator) && !normalized.startsWith("https" + schemeSeparator)) {
            return false;
        }
        int authorityStart = normalized.indexOf(schemeSeparator) + schemeSeparator.length();
        int authorityEnd = normalized.indexOf('/', authorityStart);
        String authority = authorityEnd < 0 ? normalized.substring(authorityStart) : normalized.substring(authorityStart, authorityEnd);
        return authority.indexOf('@') >= 0;
    }

    /**
     * Checks whether the URL starts with an allowed prefix and continues after it.
     *
     * @param url trimmed repository URL.
     * @param allowedPrefixes allowed URL prefixes.
     * @return whether an allowed prefix matches with text after it.
     */
    private static boolean startsWithAllowedPrefix(String url, List<String> allowedPrefixes) {
        for (String prefix : allowedPrefixes) {
            if (url.startsWith(prefix) && url.length() > prefix.length()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether a nullable value carries text.
     *
     * @param value nullable value.
     * @return whether the value is non-blank.
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
