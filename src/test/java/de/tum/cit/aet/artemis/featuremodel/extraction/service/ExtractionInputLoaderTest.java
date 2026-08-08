package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.extraction.artifact.Sha256Digest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import tools.jackson.databind.ObjectMapper;

/** Verifies that one manifest read owns every manifest-derived value of an extraction command. */
class ExtractionInputLoaderTest {

    private static final Path MANIFEST = Path.of("src/test/resources/extraction/mini-artemis-manifest.yml");

    private static final String PINNED_COMMIT = "aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee";

    @TempDir
    private Path outputRoot;

    @Test
    void bindsParsedManifestDigestCommitAndLayoutToOneByteRead() throws Exception {
        byte[] expectedBytes = Files.readAllBytes(MANIFEST);
        AtomicInteger readCount = new AtomicInteger();
        ExtractionInputLoader loader = new ExtractionInputLoader(new ObjectMapper(), path -> {
            assertThat(path).isEqualTo(MANIFEST);
            readCount.incrementAndGet();
            return expectedBytes;
        });
        FeatureExtractionInputs inputs = new FeatureExtractionInputs(null, MANIFEST, MANIFEST, MANIFEST, outputRoot);

        ExtractionRunContext context = loader.runContext(inputs);

        assertThat(readCount).hasValue(1);
        assertThat(context.manifestBytes()).containsExactly(expectedBytes);
        assertThat(context.manifest().artemisCommitSha()).isEqualTo(PINNED_COMMIT);
        assertThat(context.manifestDigest()).isEqualTo(Sha256Digest.of(expectedBytes));
        assertThat(context.artemisCommit()).isEqualTo(PINNED_COMMIT);
        assertThat(context.layout()).isEqualTo(ExtractionArtifactLayout.forCommit(outputRoot, PINNED_COMMIT));

        context.manifestBytes()[0] = 0;
        assertThat(context.manifestBytes()).containsExactly(expectedBytes);
    }
}
