package de.tum.cit.aet.artemis.featuremodel.extraction.artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Computes the prefixed lowercase SHA-256 digests used by extraction artifact envelopes and snapshots. */
public final class Sha256Digest {

    private static final String DIGEST_PREFIX = "sha256:";

    private Sha256Digest() {
    }

    /**
     * Computes the digest of a file's exact bytes.
     *
     * @param file file to hash.
     * @return prefixed lowercase hexadecimal digest.
     * @throws IOException if the file cannot be read.
     */
    public static String of(Path file) throws IOException {
        return of(Files.readAllBytes(file));
    }

    /**
     * Computes the digest of an in-memory payload.
     *
     * @param bytes exact payload bytes.
     * @return prefixed lowercase hexadecimal digest.
     */
    public static String of(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return DIGEST_PREFIX + HexFormat.of().formatHex(digest.digest(bytes));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    }
}
