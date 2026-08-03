package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;

/**
 * Lexically scans production sources for usage evidence: {@code @FeatureToggle} guards, {@code jhiFeatureToggle*}
 * template directives, and {@code @Conditional} sites referencing {@code *Enabled} condition classes. This scan is
 * deliberately line-based: it counts observed usage sites and does not interpret semantics, so structural parsing of
 * thousands of files would add cost without adding information.
 */
class UsageEvidenceScan {

    private static final Pattern FEATURE_TOGGLE_ANNOTATION_PATTERN = Pattern.compile("@FeatureToggle\\(");

    private static final Pattern FEATURE_REFERENCE_PATTERN = Pattern.compile("Feature\\.(\\w+)");

    private static final Pattern CONDITIONAL_ANNOTATION_PATTERN = Pattern.compile("@Conditional\\(");

    private static final Pattern CONDITION_CLASS_REFERENCE_PATTERN = Pattern.compile("(\\w+Enabled)\\.class");

    private static final Pattern TEMPLATE_DIRECTIVE_PATTERN = Pattern.compile("jhiFeatureToggle");

    private static final Pattern TEMPLATE_TOGGLE_REFERENCE_PATTERN = Pattern.compile("FeatureToggle\\.(\\w+)");

    /**
     * One observed usage site.
     *
     * @param file checkout-relative path.
     * @param line 1-based line of the usage.
     * @param symbol referenced toggle name or condition class name.
     */
    record UsageSite(String file, Integer line, String symbol) {
    }

    /**
     * Scan result over all usage evidence.
     *
     * @param featureToggleSites backend {@code @FeatureToggle} sites in path order.
     * @param templateToggleSites frontend template directive sites in path order.
     * @param conditionalSites backend {@code @Conditional} sites referencing condition classes, in path order.
     */
    record Result(List<UsageSite> featureToggleSites, List<UsageSite> templateToggleSites, List<UsageSite> conditionalSites) {

        /**
         * Creates an empty result for a failed or skipped scan.
         *
         * @return result without usage sites.
         */
        static Result empty() {
            return new Result(List.of(), List.of(), List.of());
        }
    }

    /**
     * Scans production sources of the given checkout for usage evidence.
     *
     * @param source Artemis source repository.
     * @return observed usage sites.
     * @throws IOException if a source tree cannot be traversed.
     */
    Result scan(ArtemisSourceRepository source) throws IOException {
        List<UsageSite> featureToggleSites = new ArrayList<>();
        List<UsageSite> conditionalSites = new ArrayList<>();
        for (String file : source.findFiles(ArtemisSourceConventions.Roots.JAVA, ArtemisSourceConventions.Naming.JAVA_SUFFIX)) {
            scanLines(source, file, featureToggleSites, conditionalSites);
        }
        List<UsageSite> templateToggleSites = new ArrayList<>();
        for (String file : source.findFiles(ArtemisSourceConventions.Roots.WEBAPP_APP, ArtemisSourceConventions.Naming.HTML_SUFFIX)) {
            scanTemplate(source, file, templateToggleSites);
        }
        return new Result(List.copyOf(featureToggleSites), List.copyOf(templateToggleSites), List.copyOf(conditionalSites));
    }

    /**
     * Scans one Java file for annotation usage sites. Only same-line references are recognized; the fixture tests pin
     * this accepted shape.
     *
     * @param source Artemis source repository.
     * @param file checkout-relative path.
     * @param featureToggleSites sink for {@code @FeatureToggle} sites.
     * @param conditionalSites sink for {@code @Conditional} sites.
     * @throws IOException if the file cannot be read.
     */
    private void scanLines(ArtemisSourceRepository source, String file, List<UsageSite> featureToggleSites, List<UsageSite> conditionalSites) throws IOException {
        List<String> lines = source.readLines(file);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (FEATURE_TOGGLE_ANNOTATION_PATTERN.matcher(line).find()) {
                collectReferences(FEATURE_REFERENCE_PATTERN, line, file, index + 1, featureToggleSites);
            }
            if (CONDITIONAL_ANNOTATION_PATTERN.matcher(line).find()) {
                collectReferences(CONDITION_CLASS_REFERENCE_PATTERN, line, file, index + 1, conditionalSites);
            }
        }
    }

    /**
     * Scans one template file for feature toggle directive usage with literal toggle references.
     *
     * @param source Artemis source repository.
     * @param file checkout-relative path.
     * @param templateToggleSites sink for template sites.
     * @throws IOException if the file cannot be read.
     */
    private void scanTemplate(ArtemisSourceRepository source, String file, List<UsageSite> templateToggleSites) throws IOException {
        List<String> lines = source.readLines(file);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (TEMPLATE_DIRECTIVE_PATTERN.matcher(line).find()) {
                collectReferences(TEMPLATE_TOGGLE_REFERENCE_PATTERN, line, file, index + 1, templateToggleSites);
            }
        }
    }

    /**
     * Collects all symbol references of a pattern on one line.
     *
     * @param pattern reference pattern with the symbol in group one.
     * @param line line text.
     * @param file checkout-relative path.
     * @param lineNumber 1-based line number.
     * @param sink usage site sink.
     */
    private void collectReferences(Pattern pattern, String line, String file, int lineNumber, List<UsageSite> sink) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            sink.add(new UsageSite(file, lineNumber, matcher.group(1)));
        }
    }
}
