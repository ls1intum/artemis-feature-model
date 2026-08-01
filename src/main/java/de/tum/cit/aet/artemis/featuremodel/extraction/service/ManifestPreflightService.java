package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import tools.jackson.databind.ObjectMapper;

/**
 * The {@code featureModelManifestPreflight} command: loads and validates the manifest through the same loader the
 * pipeline uses and reports the source commit and the manifest digest. It writes nothing, so a build or workflow can
 * resolve which Artemis commit to check out before any stage runs.
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
     * @param artemisCommitSha Artemis commit the manifest selects.
     * @param manifestDigest digest identifying the manifest content.
     * @param includeCount number of include entries.
     * @param excludeCount number of exclude entries.
     */
    public record Summary(int manifestVersion, String artemisCommitSha, String manifestDigest, int includeCount, int excludeCount) {
    }

    /**
     * Loads and validates the manifest.
     *
     * @param inputs resolved command inputs.
     * @return preflight summary.
     * @throws IOException if the manifest cannot be read.
     * @throws de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureManifestException if the manifest is invalid.
     */
    public Summary run(FeatureExtractionInputs inputs) throws IOException {
        FeatureScopeManifest manifest = inputLoader.manifest(inputs);
        return new Summary(manifest.manifestVersion(), manifest.artemisCommitSha(), inputLoader.manifestDigest(inputs), manifest.include().size(),
                manifest.exclude().size());
    }
}
