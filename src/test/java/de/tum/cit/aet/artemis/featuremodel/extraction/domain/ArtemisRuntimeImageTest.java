package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Covers the delivery-configuration runtime image reference: image required, digest pinned or the migration value. */
class ArtemisRuntimeImageTest {

    private static final String PINNED_DIGEST = "sha256:46ffd1b73a0300d719bfa915449eb61405e5c743a768a7d9d166df7e8c200a94";

    @Test
    void acceptsAPinnedDigest() {
        ArtemisRuntimeImage image = new ArtemisRuntimeImage("ghcr.io/ls1intum/artemis", PINNED_DIGEST);

        assertThat(image.digest()).isEqualTo(PINNED_DIGEST);
    }

    @Test
    void acceptsTheMigrationValueLatest() {
        ArtemisRuntimeImage image = new ArtemisRuntimeImage("ghcr.io/ls1intum/artemis", ArtemisRuntimeImage.DIGEST_LATEST);

        assertThat(image.digest()).isEqualTo(ArtemisRuntimeImage.DIGEST_LATEST);
    }

    @Test
    void rejectsABlankImage() {
        assertThatThrownBy(() -> new ArtemisRuntimeImage(" ", PINNED_DIGEST)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("remote image repository");
    }

    @Test
    void rejectsAMutableTagAsDigest() {
        assertThatThrownBy(() -> new ArtemisRuntimeImage("ghcr.io/ls1intum/artemis", "v8.3.1")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sha256:");
    }

    @Test
    void rejectsAMissingDigest() {
        assertThatThrownBy(() -> new ArtemisRuntimeImage("ghcr.io/ls1intum/artemis", null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sha256:");
    }
}
