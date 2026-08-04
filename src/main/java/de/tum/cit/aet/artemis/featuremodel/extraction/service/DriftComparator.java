package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureSource;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigurationDefaults;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;

/**
 * Compares the extraction result against the active curated feature model and the config key catalog. Matching keys
 * on config keys and condition classes, never on feature ids: id mapping is a later curation concern. The findings of
 * this comparator replace the discovery step of the manual weekly consistency audit.
 */
class DriftComparator {

    private static final String ENABLED_KEY_SUFFIX = ".enabled";

    private static final Pattern EVIDENCE_REFERENCE_PATTERN = Pattern.compile("([^:]+):([0-9,\\-]+)");

    /** Checkout roots searched when resolving curated evidence file names. */
    /**
     * Compares candidates and scanned keys against the curated model and catalog.
     *
     * @param source Artemis source repository used to verify curated evidence references.
     * @param curatedModel active curated feature model.
     * @param catalog curated config key catalog.
     * @param candidates extracted feature candidates.
     * @param yamlScan configuration defaults scan, used to check catalog value keys.
     * @param artemisCommit resolved commit of the scanned checkout.
     * @return drift report items in discovery order; the report assembly sorts them.
     */
    List<ReportItem> compare(ArtemisSourceRepository source, FeatureModel curatedModel, ArtemisConfigKeyCatalog catalog, List<FeatureCandidate> candidates,
            ExtractedConfigurationDefaults yamlScan, String artemisCommit) {
        List<ReportItem> items = new ArrayList<>();
        Map<String, FeatureCandidate> moduleCandidatesByConfigKey = new LinkedHashMap<>();
        Map<String, FeatureCandidate> moduleCandidatesByConditionClass = new LinkedHashMap<>();
        Set<String> scannedClientConstants = new LinkedHashSet<>();
        for (FeatureCandidate candidate : candidates) {
            if (!FeatureCandidate.KIND_MODULE_FEATURE.equals(candidate.kind())) {
                continue;
            }
            if (candidate.configKey() != null) {
                moduleCandidatesByConfigKey.putIfAbsent(candidate.configKey(), candidate);
            }
            if (candidate.serverConditionClass() != null) {
                moduleCandidatesByConditionClass.putIfAbsent(candidate.serverConditionClass(), candidate);
            }
            if (candidate.clientConstant() != null) {
                scannedClientConstants.add(candidate.clientConstant());
            }
        }

        Set<String> matchedCandidateIds = compareCuratedFeatures(source, curatedModel, moduleCandidatesByConfigKey, moduleCandidatesByConditionClass,
                scannedClientConstants, items);
        reportNewCandidates(candidates, matchedCandidateIds, items);
        compareCatalog(catalog, candidates, yamlScan, artemisCommit, items);
        return items;
    }

    /**
     * Compares every curated feature against the extracted module candidates and verifies its evidence references.
     *
     * @param source Artemis source repository.
     * @param curatedModel active curated feature model.
     * @param moduleCandidatesByConfigKey module candidates by config key.
     * @param moduleCandidatesByConditionClass module candidates by condition class.
     * @param scannedClientConstants client constant names observed in the scan.
     * @param items report item sink.
     * @return candidate ids matched by curated features.
     */
    private Set<String> compareCuratedFeatures(ArtemisSourceRepository source, FeatureModel curatedModel, Map<String, FeatureCandidate> moduleCandidatesByConfigKey,
            Map<String, FeatureCandidate> moduleCandidatesByConditionClass, Set<String> scannedClientConstants, List<ReportItem> items) {
        Set<String> matchedCandidateIds = new LinkedHashSet<>();
        Map<String, List<String>> fileIndex = buildEvidenceFileIndex(source, curatedModel);
        for (FeatureNode feature : curatedModel.features()) {
            FeatureSource featureSource = feature.source();
            String configKey = featureSource == null ? null : featureSource.configKey();
            String conditionClass = featureSource == null ? null : featureSource.serverConditionClass();
            if (configKey == null && conditionClass == null) {
                items.add(ReportItem.info(ReportItem.CODE_UNANCHORED_CURATED_FEATURE, feature.id(),
                        "Curated feature '" + feature.id() + "' has no config key or condition class anchor; expected for conceptual aggregates and always-on modules."));
                continue;
            }
            FeatureCandidate match = configKey == null ? null : moduleCandidatesByConfigKey.get(configKey);
            if (match == null && conditionClass != null) {
                match = moduleCandidatesByConditionClass.get(conditionClass);
            }
            if (match == null) {
                items.add(ReportItem.error(ReportItem.CODE_CURATED_ANCHOR_MISSING, feature.id(), "Curated feature '" + feature.id() + "' references anchor "
                        + describeAnchor(configKey, conditionClass) + " which the scan did not find in Artemis."));
                continue;
            }
            matchedCandidateIds.add(match.id());
            if (conditionClass != null && !conditionClass.equals(match.serverConditionClass())) {
                items.add(ReportItem.warning(ReportItem.CODE_CURATED_ANCHOR_MISSING, feature.id(), "Curated feature '" + feature.id() + "' references condition class '"
                        + conditionClass + "' but the scan attributes " + describeCandidateCondition(match) + " to candidate '" + match.id() + "'."));
            }
            String clientConstant = featureSource.clientConstant();
            if (clientConstant != null && !scannedClientConstants.contains(clientConstant)) {
                items.add(ReportItem.warning(ReportItem.CODE_CURATED_ANCHOR_MISSING, feature.id(),
                        "Curated feature '" + feature.id() + "' references client constant '" + clientConstant + "' which the scan did not find."));
            }
            verifyEvidenceReferences(source, feature, featureSource, fileIndex, items);
        }
        return matchedCandidateIds;
    }

    /**
     * Reports module and toggle candidates that no curated feature matched. Profile, infrastructure, and config key
     * candidates are excluded: technical features enter the model in a later phase, and config keys are covered by the
     * catalog drift check.
     *
     * @param candidates extracted candidates.
     * @param matchedCandidateIds candidate ids matched by curated features.
     * @param items report item sink.
     */
    private void reportNewCandidates(List<FeatureCandidate> candidates, Set<String> matchedCandidateIds, List<ReportItem> items) {
        for (FeatureCandidate candidate : candidates) {
            boolean comparableKind = FeatureCandidate.KIND_MODULE_FEATURE.equals(candidate.kind()) || FeatureCandidate.KIND_RUNTIME_TOGGLE.equals(candidate.kind());
            if (!comparableKind || matchedCandidateIds.contains(candidate.id())) {
                continue;
            }
            String anchor = candidate.configKey() != null ? " (config key " + candidate.configKey() + ")" : "";
            items.add(ReportItem.warning(ReportItem.CODE_NEW_CANDIDATE_NOT_IN_MODEL, candidate.id(),
                    "Candidate '" + candidate.id() + "'" + anchor + " exists in Artemis but has no matching feature in the curated model."));
        }
    }

    /**
     * Compares the config key catalog against the scanned keys and the scanned commit.
     *
     * @param catalog curated config key catalog.
     * @param candidates extracted candidates.
     * @param yamlScan configuration defaults scan.
     * @param artemisCommit resolved commit of the scanned checkout.
     * @param items report item sink.
     */
    private void compareCatalog(ArtemisConfigKeyCatalog catalog, List<FeatureCandidate> candidates, ExtractedConfigurationDefaults yamlScan, String artemisCommit,
            List<ReportItem> items) {
        String pin = catalog.verifiedAgainstArtemisCommit();
        if (pin == null || artemisCommit == null || !artemisCommit.startsWith(pin)) {
            items.add(ReportItem.warning(ReportItem.CODE_CONFIG_KEY_CATALOG_DRIFT, "catalog",
                    "Config key catalog is pinned to Artemis commit '" + pin + "' but the scanned checkout is at '" + artemisCommit + "'."));
        }
        Set<String> declaredEnabledKeys = new LinkedHashSet<>();
        for (FeatureCandidate candidate : candidates) {
            if (FeatureCandidate.KIND_CONFIG_KEY.equals(candidate.kind())) {
                declaredEnabledKeys.add(candidate.configKey());
            }
        }
        Set<String> catalogKeys = new LinkedHashSet<>();
        for (ArtemisConfigKeyCatalog.CatalogKey catalogKey : catalog.keys()) {
            catalogKeys.add(catalogKey.key());
            if (catalogKey.key().endsWith(ENABLED_KEY_SUFFIX)) {
                if (!declaredEnabledKeys.contains(catalogKey.key())) {
                    items.add(ReportItem.warning(ReportItem.CODE_CONFIG_KEY_CATALOG_DRIFT, catalogKey.key(),
                            "Catalog key '" + catalogKey.key() + "' is no longer declared as an enabled property constant in Artemis."));
                }
            }
            else if (!yamlScan.occurrencesByKey().containsKey(catalogKey.key())) {
                items.add(ReportItem.info(ReportItem.CODE_CONFIG_KEY_CATALOG_DRIFT, catalogKey.key(), "Catalog key '" + catalogKey.key()
                        + "' was not observed in the scanned Artemis YAML defaults; it may be declared only at an injection site, which this scan does not cover."));
            }
        }
        for (String scannedKey : declaredEnabledKeys) {
            if (!catalogKeys.contains(scannedKey)) {
                items.add(ReportItem.warning(ReportItem.CODE_CONFIG_KEY_CATALOG_DRIFT, scannedKey,
                        "Artemis declares enabled property key '" + scannedKey + "' which is missing from the config key catalog."));
            }
        }
    }

    /**
     * Verifies the file and line evidence references of one curated feature against the checkout.
     *
     * @param source Artemis source repository.
     * @param feature curated feature.
     * @param featureSource curated source block.
     * @param fileIndex evidence file name index.
     * @param items report item sink.
     */
    private void verifyEvidenceReferences(ArtemisSourceRepository source, FeatureNode feature, FeatureSource featureSource, Map<String, List<String>> fileIndex,
            List<ReportItem> items) {
        List<String> anchorTokens = anchorTokens(featureSource);
        for (String reference : featureSource.evidence()) {
            Matcher matcher = EVIDENCE_REFERENCE_PATTERN.matcher(reference);
            if (!matcher.matches()) {
                items.add(ReportItem.warning(ReportItem.CODE_CURATED_EVIDENCE_STALE, feature.id(),
                        "Evidence reference '" + reference + "' of feature '" + feature.id() + "' is not a file:lines reference and cannot be verified."));
                continue;
            }
            String fileName = matcher.group(1);
            List<Integer> lines = parseLineSpec(matcher.group(2));
            List<String> files = fileIndex.getOrDefault(fileName, List.of());
            if (files.isEmpty()) {
                items.add(ReportItem.warning(ReportItem.CODE_CURATED_EVIDENCE_STALE, feature.id(),
                        "Evidence file '" + fileName + "' of feature '" + feature.id() + "' was not found in the checkout."));
                continue;
            }
            verifyReferenceAgainstFiles(source, feature, reference, fileName, lines, files, anchorTokens, items);
        }
    }

    /**
     * Verifies one parsed evidence reference against the resolved candidate files.
     *
     * @param source Artemis source repository.
     * @param feature curated feature.
     * @param reference original evidence reference.
     * @param fileName referenced file name.
     * @param lines referenced 1-based lines.
     * @param files resolved checkout-relative paths with that file name.
     * @param anchorTokens anchor tokens of the feature.
     * @param items report item sink.
     */
    private void verifyReferenceAgainstFiles(ArtemisSourceRepository source, FeatureNode feature, String reference, String fileName, List<Integer> lines,
            List<String> files, List<String> anchorTokens, List<ReportItem> items) {
        boolean anchorSomewhereInFile = false;
        for (String file : files) {
            List<String> content = readLinesQuietly(source, file);
            if (matchesAtLines(content, lines, anchorTokens)) {
                return;
            }
            anchorSomewhereInFile = anchorSomewhereInFile || matchesAnywhere(content, anchorTokens);
        }
        if (anchorSomewhereInFile) {
            items.add(ReportItem.warning(ReportItem.CODE_CURATED_EVIDENCE_STALE, feature.id(), "Evidence reference '" + reference + "' of feature '" + feature.id()
                    + "' is stale: the anchor is still present in '" + fileName + "' but no longer at the referenced lines."));
        }
        else {
            items.add(ReportItem.warning(ReportItem.CODE_CURATED_EVIDENCE_STALE, feature.id(), "Evidence reference '" + reference + "' of feature '" + feature.id()
                    + "' is stale: no anchor token of the feature appears in '" + fileName + "' anymore."));
        }
    }

    /**
     * Builds an index from evidence file name to checkout-relative paths, walking the search roots once.
     *
     * @param source Artemis source repository.
     * @param curatedModel active curated model, used to restrict the index to referenced file names.
     * @return sorted paths per referenced file name.
     */
    private Map<String, List<String>> buildEvidenceFileIndex(ArtemisSourceRepository source, FeatureModel curatedModel) {
        Set<String> referencedFileNames = new LinkedHashSet<>();
        for (FeatureNode feature : curatedModel.features()) {
            if (feature.source() == null) {
                continue;
            }
            for (String reference : feature.source().evidence()) {
                Matcher matcher = EVIDENCE_REFERENCE_PATTERN.matcher(reference);
                if (matcher.matches()) {
                    referencedFileNames.add(matcher.group(1));
                }
            }
        }
        Map<String, List<String>> fileIndex = new LinkedHashMap<>();
        for (String root : ArtemisSourceConventions.Roots.EVIDENCE) {
            try {
                for (String file : source.findFiles(root, "")) {
                    String fileName = file.substring(file.lastIndexOf('/') + 1);
                    if (referencedFileNames.contains(fileName)) {
                        fileIndex.computeIfAbsent(fileName, unused -> new ArrayList<>()).add(file);
                    }
                }
            }
            catch (IOException e) {
                // A missing or unreadable search root leaves its files unresolved; the per-reference check reports them.
            }
        }
        fileIndex.values().forEach(files -> files.sort(String::compareTo));
        return fileIndex;
    }

    /**
     * Collects the anchor tokens of a curated source block used for evidence line verification.
     *
     * @param featureSource curated source block.
     * @return anchor tokens; config key, condition class, client constant, and the config key module segment.
     */
    private List<String> anchorTokens(FeatureSource featureSource) {
        List<String> tokens = new ArrayList<>();
        if (featureSource.configKey() != null) {
            tokens.add(featureSource.configKey());
            String[] segments = featureSource.configKey().split("\\.");
            if (segments.length >= 2) {
                tokens.add(segments[segments.length - 2]);
            }
        }
        if (featureSource.serverConditionClass() != null) {
            tokens.add(featureSource.serverConditionClass());
        }
        if (featureSource.clientConstant() != null) {
            tokens.add(featureSource.clientConstant());
        }
        return tokens;
    }

    /**
     * Checks whether any referenced line contains any anchor token.
     *
     * @param content file lines.
     * @param lines referenced 1-based lines.
     * @param anchorTokens anchor tokens.
     * @return true if one referenced line contains one token.
     */
    private boolean matchesAtLines(List<String> content, List<Integer> lines, List<String> anchorTokens) {
        for (Integer line : lines) {
            if (line < 1 || line > content.size()) {
                continue;
            }
            String text = content.get(line - 1);
            if (anchorTokens.stream().anyMatch(text::contains)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether any line of a file contains any anchor token.
     *
     * @param content file lines.
     * @param anchorTokens anchor tokens.
     * @return true if the file contains one token.
     */
    private boolean matchesAnywhere(List<String> content, List<String> anchorTokens) {
        return content.stream().anyMatch(text -> anchorTokens.stream().anyMatch(text::contains));
    }

    /**
     * Parses a line specification of single lines and ranges into individual line numbers.
     *
     * @param specification specification such as {@code 13,23} or {@code 20-21}.
     * @return referenced 1-based lines in specification order.
     */
    private List<Integer> parseLineSpec(String specification) {
        List<Integer> lines = new ArrayList<>();
        for (String part : specification.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            int rangeSeparator = part.indexOf('-');
            if (rangeSeparator > 0) {
                int from = Integer.parseInt(part.substring(0, rangeSeparator));
                int to = Integer.parseInt(part.substring(rangeSeparator + 1));
                for (int line = from; line <= to && line - from < 200; line++) {
                    lines.add(line);
                }
            }
            else {
                lines.add(Integer.parseInt(part));
            }
        }
        return lines;
    }

    /**
     * Reads file lines and treats unreadable files as empty, which the caller reports as stale evidence.
     *
     * @param source Artemis source repository.
     * @param file checkout-relative path.
     * @return file lines, or an empty list when unreadable.
     */
    private List<String> readLinesQuietly(ArtemisSourceRepository source, String file) {
        try {
            return source.readLines(file);
        }
        catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Describes the anchor of a curated feature for report messages.
     *
     * @param configKey curated config key, or null.
     * @param conditionClass curated condition class, or null.
     * @return human-readable anchor description.
     */
    private String describeAnchor(String configKey, String conditionClass) {
        if (configKey != null && conditionClass != null) {
            return "config key '" + configKey + "' / condition class '" + conditionClass + "'";
        }
        if (configKey != null) {
            return "config key '" + configKey + "'";
        }
        return "condition class '" + conditionClass + "'";
    }

    /**
     * Describes the condition class of a matched candidate for report messages.
     *
     * @param candidate matched candidate.
     * @return human-readable condition description.
     */
    private String describeCandidateCondition(FeatureCandidate candidate) {
        return candidate.serverConditionClass() == null ? "no condition class" : "condition class '" + candidate.serverConditionClass() + "'";
    }
}
