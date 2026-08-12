package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
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
     * @param includeCount number of include entries.
     * @param excludeCount number of exclude entries.
     */
    public record Summary(int manifestVersion, String artemisCommitSha, String manifestDigest, int includeCount, int excludeCount) {
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
        return new Summary(context.manifest().manifestVersion(), context.artemisCommit(), context.manifestDigest(), context.manifest().include().size(),
                context.manifest().exclude().size());
    }
}
