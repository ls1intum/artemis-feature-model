package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SourcePreflightException;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.FixtureArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;

/** Covers the pinned-source preflight: only the exact, clean, pinned checkout may be scanned. */
class ArtemisSourcePreflightTest {

    private static final Path FIXTURE_PATH = Path.of("src/test/resources/extraction/mini-artemis");

    private static final String PINNED_COMMIT = "aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee";

    private final ArtemisSourcePreflight preflight = new ArtemisSourcePreflight();

    @Test
    void acceptsTheExactCleanCheckout() {
        assertThatCode(() -> preflight.verify(FixtureArtemisSourceRepository.cleanAt(FIXTURE_PATH, PINNED_COMMIT), PINNED_COMMIT)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnotherCommit() {
        ArtemisSourceRepository source = FixtureArtemisSourceRepository.cleanAt(FIXTURE_PATH, "bbbbbbbbccccccccddddddddeeeeeeeeffffffff");

        assertThatThrownBy(() -> preflight.verify(source, PINNED_COMMIT)).isInstanceOf(SourcePreflightException.class)
                .hasMessageContaining("bbbbbbbbccccccccddddddddeeeeeeeeffffffff").hasMessageContaining(PINNED_COMMIT);
    }

    @Test
    void rejectsADirtyWorkingTree() {
        ArtemisSourceRepository source = new FixtureArtemisSourceRepository(new LocalArtemisSourceRepository(FIXTURE_PATH), PINNED_COMMIT, true);

        assertThatThrownBy(() -> preflight.verify(source, PINNED_COMMIT)).isInstanceOf(SourcePreflightException.class)
                .hasMessageContaining("uncommitted changes");
    }

    @Test
    void rejectsAnUnresolvableWorkingTreeState() {
        ArtemisSourceRepository source = new FixtureArtemisSourceRepository(new LocalArtemisSourceRepository(FIXTURE_PATH), PINNED_COMMIT, null);

        assertThatThrownBy(() -> preflight.verify(source, PINNED_COMMIT)).isInstanceOf(SourcePreflightException.class)
                .hasMessageContaining("could not be resolved");
    }

    @Test
    void rejectsACheckoutThatIsNotAGitWorkTree() {
        ArtemisSourceRepository source = new LocalArtemisSourceRepository(FIXTURE_PATH);

        assertThatThrownBy(() -> preflight.verify(source, PINNED_COMMIT)).isInstanceOf(SourcePreflightException.class)
                .hasMessageContaining("not a git work tree");
    }
}
