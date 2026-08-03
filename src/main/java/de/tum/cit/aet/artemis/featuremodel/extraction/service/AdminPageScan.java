package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceLocator;

/**
 * Scans the admin Features page component for the curated display membership lists and per-feature documentation
 * links. Identifiers in the arrays and map keys are constant or enum symbols; the assembler resolves them against the
 * frontend constant and enum scans. Only the page structure matters here — its live enablement state reflects one
 * deployment and is irrelevant to extraction.
 */
class AdminPageScan {

    private static final Pattern ARRAY_IDENTIFIER_PATTERN = Pattern.compile("^\\s*(\\w+),?\\s*$");

    private static final Pattern INLINE_IDENTIFIER_PATTERN = Pattern.compile("(\\w+)");

    private static final Pattern MAP_ENTRY_PATTERN = Pattern.compile("^\\s*\\[(\\w+(?:\\.\\w+)?)\\]:\\s*'([^']*)',?\\s*$");

    private final ArtemisSourceLocator sourceLocator = new ArtemisSourceLocator();

    /**
     * One identifier reference inside a display membership array.
     *
     * @param identifier referenced constant symbol.
     * @param line 1-based line of the reference.
     */
    record MembershipEntry(String identifier, Integer line) {
    }

    /**
     * One documentation link entry.
     *
     * @param identifier referenced constant or enum symbol, for example {@code MODULE_FEATURE_IRIS} or
     *            {@code FeatureToggle.Exports}.
     * @param url documentation link.
     * @param line 1-based line of the entry.
     */
    record DocumentationEntry(String identifier, String url, Integer line) {
    }

    /**
     * Scan result of the admin Features page component.
     *
     * @param file checkout-relative path of the component.
     * @param displayedModuleFeatures module feature membership entries in display order.
     * @param displayedProfiles profile membership entries in display order.
     * @param documentationEntries all documentation link entries of the component in source order.
     */
    record Result(String file, List<MembershipEntry> displayedModuleFeatures, List<MembershipEntry> displayedProfiles, List<DocumentationEntry> documentationEntries) {

        /**
         * Creates an empty result for a failed or skipped scan.
         *
         * @return result without entries.
         */
        static Result empty() {
            return new Result(null, List.of(), List.of(), List.of());
        }
    }

    /**
     * Scans the admin Features page component of the given checkout.
     *
     * @param source Artemis source repository.
     * @return scanned membership lists and documentation links.
     * @throws IOException if the component cannot be read.
     * @throws IllegalArgumentException if the component cannot be located.
     */
    Result scan(ArtemisSourceRepository source) throws IOException {
        String file = sourceLocator.locate(source, ArtemisSourceConventions.Files.ADMIN_FEATURE_COMPONENT, "displayedModuleFeatures field",
                content -> content.contains("displayedModuleFeatures"));
        List<String> lines = source.readLines(file);
        List<MembershipEntry> displayedModuleFeatures = scanArrayBlock(lines, "displayedModuleFeatures");
        List<MembershipEntry> displayedProfiles = scanArrayBlock(lines, "displayedProfiles");
        List<DocumentationEntry> documentationEntries = scanDocumentationEntries(lines);
        return new Result(file, displayedModuleFeatures, displayedProfiles, documentationEntries);
    }

    /**
     * Scans one identifier array field, accepting both a single-line initializer and a multi-line block. The bracket
     * detection looks only at the initializer after the assignment, because the field type annotation itself contains
     * brackets such as {@code ModuleFeature[]}.
     *
     * @param lines component lines.
     * @param fieldName array field name.
     * @return identifier entries in declaration order; empty when the field is absent.
     */
    private List<MembershipEntry> scanArrayBlock(List<String> lines, String fieldName) {
        List<MembershipEntry> entries = new ArrayList<>();
        boolean insideBlock = false;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!insideBlock) {
                int assignmentIndex = line.indexOf('=');
                if (!line.contains(fieldName) || assignmentIndex < 0) {
                    continue;
                }
                String initializer = line.substring(assignmentIndex + 1);
                int openIndex = initializer.indexOf('[');
                if (openIndex < 0) {
                    continue;
                }
                int closeIndex = initializer.indexOf(']', openIndex);
                if (closeIndex >= 0) {
                    collectInlineIdentifiers(initializer.substring(openIndex + 1, closeIndex), index + 1, entries);
                    break;
                }
                insideBlock = true;
                continue;
            }
            if (line.contains("]")) {
                break;
            }
            Matcher matcher = ARRAY_IDENTIFIER_PATTERN.matcher(line);
            if (matcher.matches()) {
                entries.add(new MembershipEntry(matcher.group(1), index + 1));
            }
        }
        return List.copyOf(entries);
    }

    /**
     * Collects the identifiers of a single-line array initializer.
     *
     * @param initializerContent text between the array brackets.
     * @param line 1-based line of the declaration.
     * @param entries membership entry sink.
     */
    private void collectInlineIdentifiers(String initializerContent, int line, List<MembershipEntry> entries) {
        Matcher matcher = INLINE_IDENTIFIER_PATTERN.matcher(initializerContent);
        while (matcher.find()) {
            entries.add(new MembershipEntry(matcher.group(1), line));
        }
    }

    /**
     * Scans all computed-key documentation link entries of the component.
     *
     * @param lines component lines.
     * @return documentation entries in source order, de-duplicated by identifier keeping the first occurrence.
     */
    private List<DocumentationEntry> scanDocumentationEntries(List<String> lines) {
        Map<String, DocumentationEntry> entriesByIdentifier = new LinkedHashMap<>();
        for (int index = 0; index < lines.size(); index++) {
            Matcher matcher = MAP_ENTRY_PATTERN.matcher(lines.get(index));
            if (matcher.matches()) {
                entriesByIdentifier.putIfAbsent(matcher.group(1), new DocumentationEntry(matcher.group(1), matcher.group(2), index + 1));
            }
        }
        return List.copyOf(entriesByIdentifier.values());
    }

}
