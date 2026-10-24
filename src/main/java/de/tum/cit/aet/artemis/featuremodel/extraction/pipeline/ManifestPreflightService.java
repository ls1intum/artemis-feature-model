package de.tum.cit.aet.artemis.featuremodel.extraction.pipeline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * The {@code featureModelManifestPreflight} command: verifies the configured checkout, loads and validates the
 * manifest through the same loader the pipeline uses, and reports the derived source revision and the manifest
 * digest. It writes nothing, so a build or workflow can confirm which Artemis commit a run would be attributed to
 * before any stage runs.
 */
public class ManifestPreflightService {

    private final ExtractionInputLoader inputLoader;

    /**
     * Creates the manifest preflight command.
     *
     * @param objectMapper Jackson mapper shared with the input loader.
     */
    public ManifestPreflightService(ObjectMapper objectMapper) {
        this.inputLoader = new ExtractionInputLoader(objectMapper);
    }

    /**
     * Machine-readable preflight result.
     *
     * @param manifestVersion loaded manifest schema version.
     * @param artemisCommitSha source revision derived from the verified checkout.
     * @param manifestDigest digest identifying the manifest content.
     * @param featureCount number of features entries.
     * @param technicalCount number of technical entries.
     * @param notModeledCount number of notModeled entries.
     * @param derivedConstraintCount number of pairwise exclusions the alternative groups of the manifest derive.
     */
    public record Summary(int manifestVersion, String artemisCommitSha, String manifestDigest, int featureCount, int technicalCount, int notModeledCount,
            int derivedConstraintCount) {
    }

    /**
     * Verifies the checkout and loads and validates the manifest.
     *
     * @param inputs resolved command inputs.
     * @param sourceFactory creates the source repository over the configured checkout.
     * @return preflight summary.
     * @throws IOException if the manifest cannot be read.
     * @throws IllegalStateException if no Artemis checkout is configured.
     * @throws de.tum.cit.aet.artemis.featuremodel.extraction.domain.SourcePreflightException if no revision can be
     *             derived, the checkout is dirty, or the derived revision differs from the expected one.
     * @throws de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureManifestException if the manifest is invalid.
     */
    public Summary run(FeatureExtractionInputs inputs, Function<Path, ArtemisSourceRepository> sourceFactory) throws IOException {
        ArtemisSourceRepository source = inputLoader.verifiedSource(inputs, sourceFactory);
        ExtractionRunContext context = inputLoader.runContext(inputs, source);
        FeatureScopeManifest manifest = context.manifest();
        return new Summary(manifest.manifestVersion(), context.artemisCommit(), context.manifestDigest(), manifest.features().size(), manifest.technical().size(),
                manifest.notModeled().size(), derivedConstraintCount(manifest));
    }

    /**
     * Counts the pairwise exclusions the manifest's alternative groups derive: for a group with {@code n} declared
     * children, {@code n * (n - 1) / 2}.
     *
     * @param manifest loaded manifest.
     * @return derived constraint count.
     */
    private int derivedConstraintCount(FeatureScopeManifest manifest) {
        int count = 0;
        for (FeatureScopeManifest.ConceptualNode group : manifest.conceptualNodes()) {
            if (!FeatureScopeManifest.GROUP_TYPE_ALTERNATIVE.equals(group.groupType())) {
                continue;
            }
            long children = manifest.conceptualNodes().stream().filter(node -> group.id().equals(node.parent())).count()
                    + manifest.features().stream().filter(entry -> group.id().equals(entry.group() != null ? entry.group() : entry.parent())).count()
                    + manifest.technical().stream().filter(entry -> group.id().equals(entry.feature().group() != null ? entry.feature().group() : entry.feature().parent())).count();
            count += (int) (children * (children - 1) / 2);
        }
        return count;
    }
}
