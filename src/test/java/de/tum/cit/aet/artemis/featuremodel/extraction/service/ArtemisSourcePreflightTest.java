package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SourcePreflightException;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.FixtureArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;

/**
 * Covers the derived-identity preflight: a revision must be derivable from a clean checkout, and when the caller
 * supplies an expected revision the derived one must equal it.
 */
class ArtemisSourcePreflightTest {

    private static final Path FIXTURE_PATH = Path.of("src/test/resources/extraction/mini-artemis");

    private static final String DERIVED_COMMIT = "aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee";

    private static final String OTHER_COMMIT = "bbbbbbbbccccccccddddddddeeeeeeeeffffffff";

    private final ArtemisSourcePreflight preflight = new ArtemisSourcePreflight();

    @Test
    void acceptsACleanCheckoutWithoutAnExpectation() {
        assertThatCode(() -> preflight.verify(FixtureArtemisSourceRepository.cleanAt(FIXTURE_PATH, DERIVED_COMMIT), null)).doesNotThrowAnyException();
    }

    @Test
    void acceptsACleanCheckoutMatchingTheExpectedRevision() {
        assertThatCode(() -> preflight.verify(FixtureArtemisSourceRepository.cleanAt(FIXTURE_PATH, DERIVED_COMMIT), DERIVED_COMMIT))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsACheckoutThatDiffersFromTheExpectedRevision() {
        ArtemisSourceRepository source = FixtureArtemisSourceRepository.cleanAt(FIXTURE_PATH, OTHER_COMMIT);

        assertThatThrownBy(() -> preflight.verify(source, DERIVED_COMMIT)).isInstanceOf(SourcePreflightException.class)
                .hasMessageContaining(OTHER_COMMIT).hasMessageContaining(DERIVED_COMMIT).hasMessageContaining("expects");
    }

    @Test
    void rejectsADirtyWorkingTree() {
        ArtemisSourceRepository source = new FixtureArtemisSourceRepository(new LocalArtemisSourceRepository(FIXTURE_PATH), DERIVED_COMMIT, true);

        assertThatThrownBy(() -> preflight.verify(source, null)).isInstanceOf(SourcePreflightException.class)
                .hasMessageContaining("uncommitted changes");
    }

    @Test
    void rejectsAnUnresolvableWorkingTreeState() {
        ArtemisSourceRepository source = new FixtureArtemisSourceRepository(new LocalArtemisSourceRepository(FIXTURE_PATH), DERIVED_COMMIT, null);

        assertThatThrownBy(() -> preflight.verify(source, null)).isInstanceOf(SourcePreflightException.class)
                .hasMessageContaining("could not be resolved");
    }

    @Test
    void rejectsACheckoutThatIsNotAGitWorkTree() {
        ArtemisSourceRepository source = new LocalArtemisSourceRepository(FIXTURE_PATH);

        assertThatThrownBy(() -> preflight.verify(source, null)).isInstanceOf(SourcePreflightException.class)
                .hasMessageContaining("not a git work tree").hasMessageContaining("no source revision can be derived");
    }
}
