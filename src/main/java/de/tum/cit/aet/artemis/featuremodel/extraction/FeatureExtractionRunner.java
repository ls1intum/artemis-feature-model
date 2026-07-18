package de.tum.cit.aet.artemis.featuremodel.extraction;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.ExtractionOutputWriter;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.FeatureExtractionService;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.FeatureManifestLoader;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.repository.JsonGuidedWorkflowStore;
import tools.jackson.databind.ObjectMapper;

/**
 * Command line entry point of the {@code extractFeatureModel} Gradle task. Runs the extraction, manifest curation,
 * and generated-model assembly pipeline against a local Artemis checkout without a Spring context and writes all
 * outputs under the given output root, in a directory named after the resolved Artemis commit.
 */
public final class FeatureExtractionRunner {

    private static final String CATALOG_RESOURCE = "classpath:feature-model/artemis-config-key-catalog.json";

    private static final String WORKFLOW_RESOURCE = "classpath:feature-model/guided-workflow.json";

    private static final String PROFILE_RESOURCE = "classpath:deployment-profiles/default-artemis-profile.json";

    private FeatureExtractionRunner() {
    }

    /**
     * Runs one extraction scan.
     *
     * @param args first argument: Artemis checkout path; second argument: output root directory; third argument: scope
     *            manifest path.
     * @throws Exception if the scan cannot run at all; individual extractor failures only produce report items.
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: FeatureExtractionRunner <artemisPath> <outputRoot> <manifestPath>");
            System.exit(1);
        }
        ObjectMapper objectMapper = new ObjectMapper();
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        LocalArtemisSourceRepository source = new LocalArtemisSourceRepository(Path.of(args[0]));
        FeatureModel curatedModel = new JsonFeatureModelStore(resourceLoader, objectMapper).loadActiveModel();
        ArtemisConfigKeyCatalog catalog = readResource(objectMapper, CATALOG_RESOURCE, ArtemisConfigKeyCatalog.class);
        FeatureScopeManifest manifest = new FeatureManifestLoader().load(Path.of(args[2]));
        GuidedWorkflow bundledWorkflow = new JsonGuidedWorkflowStore(resourceLoader, objectMapper).loadActiveWorkflow();
        DeploymentProfile bundledProfile = readResource(objectMapper, PROFILE_RESOURCE, DeploymentProfile.class);

        String scanStartedAt = Instant.now().toString();
        FeatureExtractionService.Outcome outcome = new FeatureExtractionService(objectMapper).extract(source, curatedModel, catalog, manifest, bundledWorkflow,
                bundledProfile);
        String scanFinishedAt = Instant.now().toString();

        ScanMetadata metadata = new ScanMetadata(FeatureExtractionService.EXTRACTOR_VERSION, source.root().toString(), source.commit(), source.workingTreeDirty(),
                scanStartedAt, scanFinishedAt, outcome.candidates().size(), outcome.evidence().size(), outcome.relationCandidates().size(),
                outcome.report().items().size());
        Path outputDirectory = Path.of(args[1]).resolve(source.commit());
        ExtractionOutputWriter writer = new ExtractionOutputWriter(objectMapper);
        writer.writeAll(outputDirectory, metadata, outcome);
        writer.writeSnapshot(outputDirectory, outcome, readResourceBytes(resourceLoader, WORKFLOW_RESOURCE), source.root().toString(), source.commit());

        printSummary(outcome, outputDirectory);
    }

    /**
     * Reads and parses a classpath JSON resource.
     *
     * @param <T> payload type.
     * @param objectMapper Jackson mapper.
     * @param location classpath resource location.
     * @param type payload class.
     * @return parsed payload.
     * @throws Exception if the resource cannot be read.
     */
    private static <T> T readResource(ObjectMapper objectMapper, String location, Class<T> type) throws Exception {
        Resource resource = new DefaultResourceLoader().getResource(location);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, type);
        }
    }

    /**
     * Reads the raw bytes of a classpath resource.
     *
     * @param resourceLoader Spring resource loader.
     * @param location classpath resource location.
     * @return resource bytes.
     * @throws Exception if the resource cannot be read.
     */
    private static byte[] readResourceBytes(DefaultResourceLoader resourceLoader, String location) throws Exception {
        try (InputStream inputStream = resourceLoader.getResource(location).getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    /**
     * Prints a human-readable scan summary to standard output.
     *
     * @param outcome extraction outcome.
     * @param outputDirectory directory the outputs were written to.
     */
    private static void printSummary(FeatureExtractionService.Outcome outcome, Path outputDirectory) {
        System.out.println("Feature extraction finished.");
        System.out.println("  Candidates: " + outcome.candidates().size());
        System.out.println("  Evidence items: " + outcome.evidence().size());
        System.out.println("  Relation candidates: " + outcome.relationCandidates().size());
        System.out.println("  Report items: " + outcome.report().items().size() + " " + describeCounts(outcome.report().severityCounts()));
        System.out.println("  Curation: " + outcome.report().curation().stateCounts());
        outcome.report().codeCounts().forEach((code, count) -> System.out.println("    " + code + ": " + count));
        if (outcome.generatedModel() != null) {
            System.out.println("  Generated model: " + outcome.generatedModel().features().size() + " features, " + outcome.generatedModel().relations().size()
                    + " relations, " + outcome.generatedModel().constraints().size() + " constraints (version " + outcome.generatedModel().model().version() + ")");
            System.out.println("  Generated catalog: " + outcome.generatedCatalog().keys().size() + " keys");
            System.out.println("  Model diff: " + outcome.modelDiff().classificationCounts());
            System.out.println("  Guided workflow validation: " + outcome.guidedWorkflowValidation().status());
        }
        System.out.println("  Output: " + outputDirectory);
    }

    /**
     * Formats the severity counts of the report.
     *
     * @param severityCounts item counts per severity.
     * @return formatted counts.
     */
    private static String describeCounts(Map<String, Integer> severityCounts) {
        return severityCounts.toString();
    }
}
