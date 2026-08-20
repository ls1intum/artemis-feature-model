package de.tum.cit.aet.artemis.featuremodel.extraction.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.extraction.artifact.Sha256Digest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SourcePreflightException;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.FixtureArtemisSourceRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies that one manifest read owns every manifest-derived value of an extraction command, and that the run
 * identity comes from the verified checkout instead of the manifest.
 */
class ExtractionInputLoaderTest {

    private static final Path MANIFEST = Path.of("src/test/resources/extraction/mini-artemis-manifest.yml");

    private static final Path FIXTURE_PATH = Path.of("src/test/resources/extraction/mini-artemis");

    private static final String DERIVED_COMMIT = "aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee";

    private static final String OTHER_COMMIT = "bbbbbbbbccccccccddddddddeeeeeeeeffffffff";

    @TempDir
    private Path outputRoot;

    @Test
    void bindsParsedManifestDigestDerivedRevisionAndLayoutToOneByteRead() throws Exception {
        byte[] expectedBytes = Files.readAllBytes(MANIFEST);
        AtomicInteger readCount = new AtomicInteger();
        ExtractionInputLoader loader = new ExtractionInputLoader(new ObjectMapper(), path -> {
            assertThat(path).isEqualTo(MANIFEST);
            readCount.incrementAndGet();
            return expectedBytes;
        });
        FeatureExtractionInputs inputs = new FeatureExtractionInputs(FIXTURE_PATH, MANIFEST, MANIFEST, MANIFEST, MANIFEST, outputRoot);

        ExtractionRunContext context = loader.runContext(inputs, FixtureArtemisSourceRepository.cleanAt(FIXTURE_PATH, DERIVED_COMMIT));

        assertThat(readCount).hasValue(1);
        assertThat(context.manifestBytes()).containsExactly(expectedBytes);
        assertThat(context.manifestDigest()).isEqualTo(Sha256Digest.of(expectedBytes));
        assertThat(context.artemisCommit()).isEqualTo(DERIVED_COMMIT);
        assertThat(context.layout()).isEqualTo(ExtractionArtifactLayout.forCommit(outputRoot, DERIVED_COMMIT));

        context.manifestBytes()[0] = 0;
        assertThat(context.manifestBytes()).containsExactly(expectedBytes);
    }

    @Test
    void sameManifestBytesAtTwoRevisionsProduceDistinctLayouts() throws Exception {
        ExtractionInputLoader loader = new ExtractionInputLoader(new ObjectMapper());
        FeatureExtractionInputs inputs = new FeatureExtractionInputs(FIXTURE_PATH, MANIFEST, MANIFEST, MANIFEST, MANIFEST, outputRoot);

        ExtractionRunContext firstRevision = loader.runContext(inputs, FixtureArtemisSourceRepository.cleanAt(FIXTURE_PATH, DERIVED_COMMIT));
        ExtractionRunContext secondRevision = loader.runContext(inputs, FixtureArtemisSourceRepository.cleanAt(FIXTURE_PATH, OTHER_COMMIT));

        assertThat(firstRevision.manifestDigest()).isEqualTo(secondRevision.manifestDigest());
        assertThat(firstRevision.layout()).isNotEqualTo(secondRevision.layout());
        assertThat(firstRevision.layout().root()).isEqualTo(outputRoot.resolve(DERIVED_COMMIT));
        assertThat(secondRevision.layout().root()).isEqualTo(outputRoot.resolve(OTHER_COMMIT));
    }

    @Test
    void verifiedSourceRejectsAnExpectedRevisionMismatch() {
        ExtractionInputLoader loader = new ExtractionInputLoader(new ObjectMapper());
        FeatureExtractionInputs inputs = new FeatureExtractionInputs(FIXTURE_PATH, MANIFEST, FeatureExtractionInputs.MANIFEST_SOURCE_REPOSITORY, MANIFEST, MANIFEST, MANIFEST, outputRoot,
                DERIVED_COMMIT);

        assertThatThrownBy(() -> loader.verifiedSource(inputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, OTHER_COMMIT)))
                .isInstanceOf(SourcePreflightException.class).hasMessageContaining(DERIVED_COMMIT).hasMessageContaining(OTHER_COMMIT);
    }
}
