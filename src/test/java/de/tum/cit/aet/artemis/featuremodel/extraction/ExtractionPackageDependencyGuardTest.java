package de.tum.cit.aet.artemis.featuremodel.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

/**
 * Dependency-free source guard for the acyclic dependency directions between the extraction capability packages.
 * Each capability package may import only its declared collaborators: the principal directions of the package
 * refactoring plan plus the runtime contract edges into {@code catalog}, {@code deployment}, {@code export},
 * {@code selection}, and {@code shared}. The key rule is that {@code pipeline} and {@code report} never depend on a
 * capability package, so no package cycle can form.
 */
class ExtractionPackageDependencyGuardTest {

    private static final Path FEATURE_MODEL_SOURCES = Path.of("src/main/java/de/tum/cit/aet/artemis/featuremodel");

    private static final Path EXTRACTION_SOURCES = FEATURE_MODEL_SOURCES.resolve("extraction");

    private static final String FEATURE_MODEL_IMPORT_PREFIX = "import de.tum.cit.aet.artemis.featuremodel.";

    /** Allowed featuremodel import prefixes per extraction capability package. */
    private static final Map<String, Set<String>> ALLOWED_DEPENDENCIES = Map.of(
            "pipeline",
            Set.of("extraction.pipeline", "extraction.report", "extraction.domain", "extraction.artifact", "extraction.repository", "extraction.source",
                    "catalog", "deployment", "export"),
            "scan", Set.of("extraction.scan", "extraction.pipeline", "extraction.domain", "extraction.repository", "extraction.source"),
            "model",
            Set.of("extraction.model", "extraction.pipeline", "extraction.report", "extraction.domain", "extraction.repository", "catalog", "deployment",
                    "export", "shared"),
            "workflow",
            Set.of("extraction.workflow", "extraction.pipeline", "extraction.domain", "extraction.repository", "catalog", "deployment", "selection", "shared"),
            "report", Set.of("extraction.report", "extraction.domain"),
            "snapshot",
            Set.of("extraction.snapshot", "extraction.pipeline", "extraction.report", "extraction.domain", "extraction.artifact", "extraction.repository",
                    "catalog", "export", "selection"));

    @Test
    void capabilityPackagesRespectTheDeclaredDependencyDirections() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Set<String>> capability : new TreeMap<>(ALLOWED_DEPENDENCIES).entrySet()) {
            Path packageDirectory = EXTRACTION_SOURCES.resolve(capability.getKey());
            assertThat(packageDirectory).as("capability package directory %s", capability.getKey()).isDirectory();
            for (Path sourceFile : javaFilesIn(packageDirectory)) {
                collectViolations(sourceFile, capability.getKey(), capability.getValue(), violations);
            }
        }
        assertThat(violations).as("extraction package dependency directions").isEmpty();
    }

    @Test
    void nothingImportsTheDissolvedServicePackage() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path sourceFile : javaFilesIn(FEATURE_MODEL_SOURCES)) {
            for (String line : Files.readAllLines(sourceFile)) {
                if (line.startsWith("import de.tum.cit.aet.artemis.featuremodel.extraction.service.")) {
                    violations.add(sourceFile + " imports the dissolved extraction.service package");
                }
            }
        }
        assertThat(violations).as("references to the dissolved extraction.service package").isEmpty();
    }

    /**
     * Records every featuremodel import of one source file that leaves the allowed dependency directions.
     *
     * @param sourceFile source file to check.
     * @param capability extraction capability package the file belongs to.
     * @param allowedPrefixes allowed featuremodel package prefixes.
     * @param violations sink for violation descriptions.
     * @throws IOException if the source file cannot be read.
     */
    private void collectViolations(Path sourceFile, String capability, Set<String> allowedPrefixes, List<String> violations) throws IOException {
        for (String line : Files.readAllLines(sourceFile)) {
            if (!line.startsWith(FEATURE_MODEL_IMPORT_PREFIX)) {
                continue;
            }
            String imported = line.substring(FEATURE_MODEL_IMPORT_PREFIX.length(), line.length() - 1);
            if (!isAllowed(imported, allowedPrefixes)) {
                violations.add("extraction." + capability + " -> " + imported + " in " + sourceFile.getFileName());
            }
        }
    }

    /**
     * Decides whether one imported featuremodel type lies inside the allowed package prefixes.
     *
     * @param imported imported name relative to the featuremodel root package.
     * @param allowedPrefixes allowed featuremodel package prefixes.
     * @return true when some allowed prefix contains the import.
     */
    private boolean isAllowed(String imported, Set<String> allowedPrefixes) {
        for (String prefix : allowedPrefixes) {
            if (imported.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lists every Java source file below one directory.
     *
     * @param directory root directory.
     * @return regular {@code .java} files in stable order.
     * @throws IOException if the tree cannot be walked.
     */
    private List<Path> javaFilesIn(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            return paths.filter(path -> path.toString().endsWith(".java")).filter(Files::isRegularFile).sorted().toList();
        }
    }
}
