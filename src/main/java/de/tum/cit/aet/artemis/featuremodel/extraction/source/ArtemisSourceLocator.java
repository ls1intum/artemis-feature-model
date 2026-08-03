package de.tum.cit.aet.artemis.featuremodel.extraction.source;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions.SourceFileTarget;

/** Locates convention-owned source targets through an exact path or one deterministic, verified fallback search. */
public final class ArtemisSourceLocator {

    /**
     * Locates a target whose exact file name is sufficient verification.
     *
     * @param source source checkout boundary.
     * @param target convention-owned target descriptor.
     * @return checkout-relative located path.
     * @throws IOException if the fallback root cannot be searched.
     * @throws IllegalArgumentException if no target or more than one target is found.
     */
    public String locate(ArtemisSourceRepository source, SourceFileTarget target) throws IOException {
        return locate(source, target, "file name", unused -> true);
    }

    /**
     * Locates a target, verifying fallback candidates by their source content. The preferred exact path remains
     * authoritative and is parsed by the owning scanner, matching the established source contract.
     *
     * @param source source checkout boundary.
     * @param target convention-owned target descriptor.
     * @param verifierDescription description of the required source marker.
     * @param contentVerifier scanner-specific content predicate.
     * @return checkout-relative located path.
     * @throws IOException if the fallback root cannot be searched.
     * @throws IllegalArgumentException if no verified target or more than one verified target is found.
     */
    public String locate(ArtemisSourceRepository source, SourceFileTarget target, String verifierDescription, Predicate<String> contentVerifier)
            throws IOException {
        if (source.fileExists(target.preferredPath())) {
            return target.preferredPath();
        }

        List<String> verifiedMatches = new ArrayList<>();
        for (String candidate : source.findFilesByName(target.fallbackRoot(), target.fileName()).stream().sorted().toList()) {
            requireWithinFallbackRoot(candidate, target);
            try {
                if (contentVerifier.test(source.readFile(candidate))) {
                    verifiedMatches.add(candidate);
                }
            }
            catch (IOException e) {
                // An unreadable sibling is not a verified target; the final error still names the bounded search.
            }
        }
        if (verifiedMatches.isEmpty()) {
            throw new IllegalArgumentException("No " + target.description() + " file named " + target.fileName() + " matching " + verifierDescription
                    + " found under " + target.fallbackRoot() + ".");
        }
        if (verifiedMatches.size() > 1) {
            throw new IllegalArgumentException("Ambiguous " + target.description() + " target: multiple verified " + target.fileName() + " files matching "
                    + verifierDescription + " found under " + target.fallbackRoot() + ": " + verifiedMatches + ".");
        }
        return verifiedMatches.getFirst();
    }

    /**
     * Rejects a repository implementation that returns a fallback candidate outside the descriptor's bounded root.
     *
     * @param candidate checkout-relative candidate path.
     * @param target convention-owned target descriptor.
     * @throws IllegalArgumentException if the candidate is absolute or outside the fallback root.
     */
    private void requireWithinFallbackRoot(String candidate, SourceFileTarget target) {
        Path path = Path.of(candidate);
        String normalized = path.normalize().toString().replace('\\', '/');
        String root = Path.of(target.fallbackRoot()).normalize().toString().replace('\\', '/');
        if (path.isAbsolute() || normalized.startsWith("../") || !normalized.startsWith(root + "/")) {
            throw new IllegalArgumentException("Located " + target.description() + " candidate escapes fallback root " + target.fallbackRoot() + ": " + candidate
                    + ".");
        }
    }
}
