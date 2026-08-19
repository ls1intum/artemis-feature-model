package de.tum.cit.aet.artemis.featuremodel.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.extraction.artifact.Sha256Digest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureManifestException;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GeneratedSnapshotMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SnapshotProvenance;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SourcePreflightException;
import de.tum.cit.aet.artemis.featuremodel.extraction.model.ModelStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.SyntheticArtemisCheckout;
import de.tum.cit.aet.artemis.featuremodel.extraction.scan.ScanStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.snapshot.PackageStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.workflow.WorkflowStageService;
import tools.jackson.databind.ObjectMapper;

/**
 * Proves {@code checkout} mode end to end against synthetic git repositories, without any upstream Artemis change:
 * canonical-path manifest resolution, real HEAD derivation, dirty-tree rejection, revision-keyed layouts, manifest
 * digest binding, and the repository-mode overlap-window guard.
 */
class CheckoutModeExtractionTest {

    private static final Path FIXTURE_PATH = Path.of("src/test/resources/extraction/mini-artemis");

    private static final Path FIXTURE_INPUTS = Path.of("src/test/resources/extraction/fixture-inputs");

    private static final Path MANIFEST = Path.of("src/test/resources/extraction/mini-artemis-manifest.yml");

    private static final String PINNED_REPOSITORY_COMMIT = "fedcba9876543210fedcba9876543210fedcba98";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    private Path checkoutDirectory;

    @TempDir
    private Path outputRoot;

    @Test
    void checkoutModePipelinePublishesASnapshotBoundToTheDerivedHeadAndManifestDigest() throws Exception {
        SyntheticArtemisCheckout checkout = SyntheticArtemisCheckout.create(checkoutDirectory, FIXTURE_PATH);
        byte[] manifestBytes = Files.readAllBytes(MANIFEST);
        String head = checkout.commitCheckoutManifest(manifestBytes);
        FeatureExtractionInputs inputs = checkoutModeInputs(checkout);

        runPipeline(inputs);

        ExtractionArtifactLayout layout = ExtractionArtifactLayout.forCommit(outputRoot, head);
        assertThat(layout.snapshotDirectory()).isDirectory();
        SnapshotProvenance provenance = OBJECT_MAPPER.readValue(Files.readAllBytes(layout.snapshotDirectory().resolve("provenance.json")),
                SnapshotProvenance.class);
        assertThat(provenance.manifestSource()).isEqualTo(SnapshotProvenance.MANIFEST_SOURCE_CHECKOUT);
        assertThat(provenance.artemisCommit()).isEqualTo(head);
        assertThat(provenance.manifestDigest()).isEqualTo(Sha256Digest.of(manifestBytes));
        GeneratedSnapshotMetadata metadata = OBJECT_MAPPER.readValue(Files.readAllBytes(layout.snapshotDirectory().resolve("metadata.json")),
                GeneratedSnapshotMetadata.class);
        assertThat(metadata.snapshotId()).startsWith("generated-" + head.substring(0, 12) + "-");
    }

    @Test
    void checkoutModeFailsNamingTheCanonicalPathWhenTheManifestIsAbsent() throws Exception {
        SyntheticArtemisCheckout checkout = SyntheticArtemisCheckout.create(checkoutDirectory, FIXTURE_PATH);
        FeatureExtractionInputs inputs = checkoutModeInputs(checkout);

        assertThatThrownBy(() -> new ScanStageService(OBJECT_MAPPER).run(inputs, LocalArtemisSourceRepository::new))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(FeatureExtractionInputs.CHECKOUT_MANIFEST_RELATIVE_PATH)
                .hasMessageContaining(checkout.root().toString());
    }

    @Test
    void checkoutModeRejectsADirtyWorkingTreeBeforeReadingAnything() throws Exception {
        SyntheticArtemisCheckout checkout = SyntheticArtemisCheckout.create(checkoutDirectory, FIXTURE_PATH);
        checkout.commitCheckoutManifest(Files.readAllBytes(MANIFEST));
        checkout.makeDirty();
        FeatureExtractionInputs inputs = checkoutModeInputs(checkout);

        assertThatThrownBy(() -> new ScanStageService(OBJECT_MAPPER).run(inputs, LocalArtemisSourceRepository::new))
                .isInstanceOf(SourcePreflightException.class).hasMessageContaining("uncommitted changes");
        assertThat(outputRoot).isEmptyDirectory();
    }

    @Test
    void sameManifestAtTwoRevisionsYieldsDistinctLayoutsAndSnapshotIdentities() throws Exception {
        SyntheticArtemisCheckout checkout = SyntheticArtemisCheckout.create(checkoutDirectory, FIXTURE_PATH);
        String firstHead = checkout.commitCheckoutManifest(Files.readAllBytes(MANIFEST));
        FeatureExtractionInputs inputs = checkoutModeInputs(checkout);
        runPipeline(inputs);

        String secondHead = checkout.commitMarkerRevision();
        runPipeline(inputs);

        assertThat(secondHead).isNotEqualTo(firstHead);
        ExtractionArtifactLayout firstLayout = ExtractionArtifactLayout.forCommit(outputRoot, firstHead);
        ExtractionArtifactLayout secondLayout = ExtractionArtifactLayout.forCommit(outputRoot, secondHead);
        assertThat(firstLayout.snapshotDirectory()).isDirectory();
        assertThat(secondLayout.snapshotDirectory()).isDirectory();
        GeneratedSnapshotMetadata firstMetadata = OBJECT_MAPPER.readValue(Files.readAllBytes(firstLayout.snapshotDirectory().resolve("metadata.json")),
                GeneratedSnapshotMetadata.class);
        GeneratedSnapshotMetadata secondMetadata = OBJECT_MAPPER.readValue(Files.readAllBytes(secondLayout.snapshotDirectory().resolve("metadata.json")),
                GeneratedSnapshotMetadata.class);
        assertThat(firstMetadata.snapshotId()).isNotEqualTo(secondMetadata.snapshotId());
        SnapshotProvenance firstProvenance = OBJECT_MAPPER.readValue(Files.readAllBytes(firstLayout.snapshotDirectory().resolve("provenance.json")),
                SnapshotProvenance.class);
        SnapshotProvenance secondProvenance = OBJECT_MAPPER.readValue(Files.readAllBytes(secondLayout.snapshotDirectory().resolve("provenance.json")),
                SnapshotProvenance.class);
        assertThat(firstProvenance.manifestDigest()).isEqualTo(secondProvenance.manifestDigest());
    }

    @Test
    void repositoryModeAcceptsAByteIdenticalCoLocatedManifest() throws Exception {
        SyntheticArtemisCheckout checkout = SyntheticArtemisCheckout.create(checkoutDirectory, FIXTURE_PATH);
        checkout.commitCheckoutManifest(Files.readAllBytes(MANIFEST));
        FeatureExtractionInputs inputs = repositoryModeInputs(checkout);

        ScanStageService.Summary summary = new ScanStageService(OBJECT_MAPPER).run(inputs, LocalArtemisSourceRepository::new);

        assertThat(summary.artemisCommit()).isEqualTo(checkout.head());
    }

    @Test
    void repositoryModeRejectsADivergingCoLocatedManifest() throws Exception {
        SyntheticArtemisCheckout checkout = SyntheticArtemisCheckout.create(checkoutDirectory, FIXTURE_PATH);
        String divergingManifest = new String(Files.readAllBytes(MANIFEST), StandardCharsets.UTF_8) + "# diverging trailing comment\n";
        checkout.commitCheckoutManifest(divergingManifest.getBytes(StandardCharsets.UTF_8));
        FeatureExtractionInputs inputs = repositoryModeInputs(checkout);

        assertThatThrownBy(() -> new ScanStageService(OBJECT_MAPPER).run(inputs, LocalArtemisSourceRepository::new))
                .isInstanceOf(FeatureManifestException.class)
                .hasMessageContaining(FeatureExtractionInputs.CHECKOUT_MANIFEST_RELATIVE_PATH)
                .hasMessageContaining("byte-identical or absent");
    }

    /**
     * Creates checkout-mode inputs over a synthetic checkout; the repository-mode manifest path is deliberately
     * present but must be ignored.
     *
     * @param checkout synthetic checkout.
     * @return checkout-mode inputs.
     */
    private FeatureExtractionInputs checkoutModeInputs(SyntheticArtemisCheckout checkout) {
        return new FeatureExtractionInputs(checkout.root(), MANIFEST, FeatureExtractionInputs.MANIFEST_SOURCE_CHECKOUT,
                FIXTURE_INPUTS.resolve("guided-workflow.json"), FIXTURE_INPUTS.resolve("deployment-profile.json"),
                FIXTURE_INPUTS.resolve("artemis-runtime-image.json"), outputRoot, null);
    }

    /**
     * Creates repository-mode inputs over a synthetic checkout, reading the committed test manifest.
     *
     * @param checkout synthetic checkout.
     * @return repository-mode inputs.
     */
    private FeatureExtractionInputs repositoryModeInputs(SyntheticArtemisCheckout checkout) {
        return new FeatureExtractionInputs(checkout.root(), MANIFEST, FIXTURE_INPUTS.resolve("guided-workflow.json"),
                FIXTURE_INPUTS.resolve("deployment-profile.json"), FIXTURE_INPUTS.resolve("artemis-runtime-image.json"), outputRoot);
    }

    /**
     * Runs the complete staged pipeline over the synthetic checkout with real git revision derivation.
     *
     * @param inputs command inputs.
     * @throws Exception if a command fails.
     */
    private void runPipeline(FeatureExtractionInputs inputs) throws Exception {
        new ScanStageService(OBJECT_MAPPER).run(inputs, LocalArtemisSourceRepository::new);
        new ModelStageService(OBJECT_MAPPER).run(inputs, LocalArtemisSourceRepository::new);
        new WorkflowStageService(OBJECT_MAPPER).run(inputs, LocalArtemisSourceRepository::new);
        new PackageStageService(OBJECT_MAPPER, PINNED_REPOSITORY_COMMIT).run(inputs, LocalArtemisSourceRepository::new);
    }
}
