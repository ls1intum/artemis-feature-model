package de.tum.cit.aet.artemis.featuremodel.extraction;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.ExtractionOutputWriter;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.FeatureExtractionService;
import tools.jackson.databind.ObjectMapper;

/**
 * Command line entry point of the {@code extractFeatureModel} Gradle task. Runs the extraction pipeline against a
 * local Artemis checkout without a Spring context and writes the five outputs under the given output root, in a
 * directory named after the resolved Artemis commit.
 */
public final class FeatureExtractionRunner {

    private static final String CATALOG_RESOURCE = "classpath:feature-model/artemis-config-key-catalog.json";

    private FeatureExtractionRunner() {
    }

    /**
     * Runs one extraction scan.
     *
     * @param args first argument: Artemis checkout path; second argument: output root directory.
     * @throws Exception if the scan cannot run at all; individual extractor failures only produce report items.
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: FeatureExtractionRunner <artemisPath> <outputRoot>");
            System.exit(1);
        }
        ObjectMapper objectMapper = new ObjectMapper();
        LocalArtemisSourceRepository source = new LocalArtemisSourceRepository(Path.of(args[0]));
        FeatureModel curatedModel = new JsonFeatureModelStore(new DefaultResourceLoader(), objectMapper).loadActiveModel();
        ArtemisConfigKeyCatalog catalog = loadCatalog(objectMapper);

        String scanStartedAt = Instant.now().toString();
        FeatureExtractionService.Outcome outcome = new FeatureExtractionService(objectMapper).extract(source, curatedModel, catalog);
        String scanFinishedAt = Instant.now().toString();

        ScanMetadata metadata = new ScanMetadata(FeatureExtractionService.EXTRACTOR_VERSION, source.root().toString(), source.commit(), source.workingTreeDirty(),
                scanStartedAt, scanFinishedAt, outcome.candidates().size(), outcome.evidence().size(), outcome.relationCandidates().size(),
                outcome.report().items().size());
        Path outputDirectory = Path.of(args[1]).resolve(source.commit());
        new ExtractionOutputWriter(objectMapper).writeAll(outputDirectory, metadata, outcome);

        printSummary(outcome, outputDirectory);
    }

    /**
     * Loads the classpath config key catalog.
     *
     * @param objectMapper Jackson mapper.
     * @return parsed catalog.
     * @throws Exception if the catalog resource cannot be read.
     */
    private static ArtemisConfigKeyCatalog loadCatalog(ObjectMapper objectMapper) throws Exception {
        Resource resource = new DefaultResourceLoader().getResource(CATALOG_RESOURCE);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, ArtemisConfigKeyCatalog.class);
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
        outcome.report().codeCounts().forEach((code, count) -> System.out.println("    " + code + ": " + count));
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
