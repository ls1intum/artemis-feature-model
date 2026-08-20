package de.tum.cit.aet.artemis.featuremodel.extraction.scan;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigurationDefault;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigurationDefaults;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.SourceScanResult;

/**
 * Scans all {@code application*.yml} configuration defaults of the checkout into a flat index of dotted keys with
 * file, line, and scalar value. The index backs candidate default states and the config key catalog drift check. A
 * single unparseable file is reported and skipped.
 */
class YamlConfigScan {

    /** Files whose defaults take precedence when a key occurs in several configuration files. */
    private static final List<String> PREFERRED_DEFAULT_FILES = List.of(ArtemisSourceConventions.Files.APPLICATION_CORE,
            ArtemisSourceConventions.Files.APPLICATION);

    /**
     * Scans all application configuration YAML files of the given checkout.
     *
     * @param source Artemis source repository.
     * @return flat key index and per-file parse errors.
     * @throws IOException if the configuration directory cannot be traversed.
     */
    SourceScanResult<ExtractedConfigurationDefaults> scan(ArtemisSourceRepository source) throws IOException {
        Map<String, List<ExtractedConfigurationDefault>> occurrencesByKey = new LinkedHashMap<>();
        List<ReportItem> errors = new ArrayList<>();
        for (String file : listApplicationFiles(source)) {
            try {
                scanFile(source, file, occurrencesByKey);
            }
            catch (IOException | RuntimeException e) {
                errors.add(ReportItem.error(ReportItem.CODE_EXTRACTOR_ERROR, file, "Could not parse configuration YAML: " + e.getMessage()));
            }
        }
        occurrencesByKey.values().forEach(occurrences -> occurrences.sort(YamlConfigScan::compareOccurrences));
        ExtractedConfigurationDefaults defaults = new ExtractedConfigurationDefaults(occurrencesByKey, List.copyOf(errors));
        return SourceScanResult.withDiagnostics(defaults, errors);
    }

    /**
     * Lists the application YAML files of the configuration directory, ordered with preferred default files first and
     * the remaining files sorted by path.
     *
     * @param source Artemis source repository.
     * @return ordered checkout-relative file paths.
     * @throws IOException if the directory cannot be traversed.
     */
    private List<String> listApplicationFiles(ArtemisSourceRepository source) throws IOException {
        List<String> files = new ArrayList<>();
        for (String file : source.findFiles(ArtemisSourceConventions.Roots.CONFIG, ArtemisSourceConventions.Naming.YAML_SUFFIX)) {
            String fileName = file.substring(file.lastIndexOf('/') + 1);
            if (fileName.startsWith(ArtemisSourceConventions.Naming.APPLICATION_FILE_PREFIX)) {
                files.add(file);
            }
        }
        files.sort(YamlConfigScan::compareFilePaths);
        return files;
    }

    /**
     * Scans one YAML file into the key index using composed nodes, which carry line marks.
     *
     * @param source Artemis source repository.
     * @param file checkout-relative path.
     * @param occurrencesByKey accumulating key index.
     * @throws IOException if the file cannot be read.
     */
    private void scanFile(ArtemisSourceRepository source, String file, Map<String, List<ExtractedConfigurationDefault>> occurrencesByKey) throws IOException {
        String content = source.readFile(file);
        for (Node document : new Yaml().composeAll(new StringReader(content))) {
            if (document instanceof MappingNode mapping) {
                collectKeys("", mapping, file, occurrencesByKey);
            }
        }
    }

    /**
     * Recursively collects scalar leaves of a mapping node into dotted keys.
     *
     * @param prefix dotted key prefix, empty at the root.
     * @param mapping current mapping node.
     * @param file checkout-relative path of the scanned file.
     * @param occurrencesByKey accumulating key index.
     */
    private void collectKeys(String prefix, MappingNode mapping, String file, Map<String, List<ExtractedConfigurationDefault>> occurrencesByKey) {
        for (NodeTuple tuple : mapping.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode keyNode)) {
                continue;
            }
            String path = prefix.isEmpty() ? keyNode.getValue() : prefix + "." + keyNode.getValue();
            if (tuple.getValueNode() instanceof MappingNode nested) {
                collectKeys(path, nested, file, occurrencesByKey);
            }
            else if (tuple.getValueNode() instanceof ScalarNode scalar) {
                int line = keyNode.getStartMark().getLine() + 1;
                occurrencesByKey.computeIfAbsent(path, unused -> new ArrayList<>()).add(new ExtractedConfigurationDefault(file, line, convertScalar(scalar.getValue())));
            }
        }
    }

    /**
     * Converts a YAML scalar text into a typed value for booleans and integers, keeping everything else textual.
     *
     * @param text raw scalar text.
     * @return typed scalar value.
     */
    private Object convertScalar(String text) {
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        if (text.matches("-?\\d+")) {
            try {
                return Long.parseLong(text);
            }
            catch (NumberFormatException e) {
                return text;
            }
        }
        return text;
    }

    /**
     * Compares occurrences so that preferred default files come first, then sorted paths, then lines.
     *
     * @param first first occurrence.
     * @param second second occurrence.
     * @return comparison result.
     */
    private static int compareOccurrences(ExtractedConfigurationDefault first, ExtractedConfigurationDefault second) {
        int fileComparison = compareFilePaths(first.file(), second.file());
        if (fileComparison != 0) {
            return fileComparison;
        }
        return Integer.compare(first.line(), second.line());
    }

    /**
     * Compares configuration file paths with the preferred default files ranked first.
     *
     * @param first first path.
     * @param second second path.
     * @return comparison result.
     */
    private static int compareFilePaths(String first, String second) {
        int firstRank = preferredRank(first);
        int secondRank = preferredRank(second);
        if (firstRank != secondRank) {
            return Integer.compare(firstRank, secondRank);
        }
        return first.compareTo(second);
    }

    /**
     * Ranks a path by its position in the preferred default file list.
     *
     * @param file checkout-relative path.
     * @return preference rank; lower ranks win.
     */
    private static int preferredRank(String file) {
        int index = PREFERRED_DEFAULT_FILES.indexOf(file);
        return index >= 0 ? index : PREFERRED_DEFAULT_FILES.size();
    }
}
