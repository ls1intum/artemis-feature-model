package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.export.domain.StaticConfigFinding;
import de.tum.cit.aet.artemis.featuremodel.export.domain.StaticConfigValidationReport;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException;
import tools.jackson.databind.ObjectMapper;

/**
 * Statically validates a generated configuration overlay against the curated Artemis configuration key catalog,
 * without booting Artemis (Workstream A1).
 *
 * <p>
 * The overlay YAML is parsed back into flat dotted keys with typed scalar values. Every entry must use a key from the
 * catalog, and its value must have the type the catalog declares for that key. Environment placeholders such as
 * {@code ${ARTEMIS_IRIS_SECRET_TOKEN}} pass every type check because their value is only known at deployment time. The
 * result is deterministic and cheap, so it can run at generation time, inside the shipped package, and as a CI gate,
 * all sharing this one rule set.
 */
@Service
public class StaticConfigValidationService {

    private static final Logger log = LoggerFactory.getLogger(StaticConfigValidationService.class);

    static final String CATALOG_RESOURCE = "classpath:feature-model/artemis-config-key-catalog.json";

    /** Matches a value that is exactly one {@code ${VARIABLE}} placeholder resolved at deployment time. */
    private static final Pattern ENV_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{[A-Z0-9_]+\\}");

    private final ArtemisConfigKeyCatalog catalog;

    private final Map<String, String> typesByKey;

    /**
     * Creates the service against the curated classpath catalog, the default catalog source.
     *
     * @param resourceLoader Spring resource loader used to resolve the classpath catalog.
     * @param objectMapper Jackson mapper used to parse the catalog.
     * @throws FeatureModelLoadException if the catalog resource cannot be read or parsed.
     */
    public StaticConfigValidationService(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this(resourceLoader, objectMapper, CATALOG_RESOURCE);
    }

    /**
     * Creates the service and eagerly loads the configured catalog, so a broken catalog fails at startup instead of
     * at the first validation. The curated classpath catalog stays the default; a maintainer may explicitly point the
     * location at a regenerated catalog produced by the extraction pipeline, for example
     * {@code file:build/feature-extraction/<commit>/generated-config-key-catalog.json}.
     *
     * @param resourceLoader Spring resource loader used to resolve the catalog location.
     * @param objectMapper Jackson mapper used to parse the catalog.
     * @param catalogLocation catalog resource location; the curated classpath catalog by default.
     * @throws FeatureModelLoadException if the catalog resource cannot be read or parsed.
     */
    @Autowired
    public StaticConfigValidationService(ResourceLoader resourceLoader, ObjectMapper objectMapper,
            @Value("${featuremodel.static-validation.catalog-location:" + CATALOG_RESOURCE + "}") String catalogLocation) {
        this.catalog = loadCatalog(resourceLoader, objectMapper, catalogLocation);
        this.typesByKey = catalog.keys().stream().collect(Collectors.toMap(ArtemisConfigKeyCatalog.CatalogKey::key, ArtemisConfigKeyCatalog.CatalogKey::type,
                (first, second) -> first, LinkedHashMap::new));
    }

    /**
     * Validates a generated overlay YAML document against the catalog.
     *
     * @param overlayYaml overlay YAML text; blank or empty documents validate as PASS with zero entries.
     * @return validation report with one finding per unknown key or type mismatch.
     * @throws IllegalArgumentException if the document is not a YAML mapping.
     */
    public StaticConfigValidationReport validate(String overlayYaml) {
        Map<String, Object> entries = flattenOverlay(overlayYaml);
        List<StaticConfigFinding> findings = new ArrayList<>();
        for (Map.Entry<String, Object> entry : entries.entrySet()) {
            validateEntry(entry.getKey(), entry.getValue(), findings);
        }
        String status = findings.isEmpty() ? StaticConfigValidationReport.STATUS_PASS : StaticConfigValidationReport.STATUS_FAIL;
        if (!findings.isEmpty()) {
            log.warn("Static config validation found {} issue(s) in {} overlay entries.", findings.size(), entries.size());
        }
        return new StaticConfigValidationReport(status, catalog.catalogVersion(), catalog.verifiedAgainstArtemisCommit(), entries.size(), findings);
    }

    /**
     * Parses the overlay YAML and flattens its nested mappings into dotted keys with scalar values, preserving
     * document order.
     *
     * @param overlayYaml overlay YAML text.
     * @return ordered map from dotted key to scalar value; empty for a blank document.
     * @throws IllegalArgumentException if the document is not a YAML mapping.
     */
    private Map<String, Object> flattenOverlay(String overlayYaml) {
        Object root = new Yaml().load(overlayYaml == null ? "" : overlayYaml);
        Map<String, Object> entries = new LinkedHashMap<>();
        if (root == null) {
            return entries;
        }
        if (!(root instanceof Map<?, ?> rootMap)) {
            throw new IllegalArgumentException("The overlay document is not a YAML mapping.");
        }
        collectEntries("", rootMap, entries);
        return entries;
    }

    /**
     * Recursively collects scalar leaves of a nested mapping into dotted keys.
     *
     * @param prefix dotted key prefix of the current mapping, empty at the root.
     * @param map current nested mapping.
     * @param entries accumulating flat entries.
     */
    private void collectEntries(String prefix, Map<?, ?> map, Map<String, Object> entries) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String path = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> nested) {
                collectEntries(path, nested, entries);
            }
            else {
                entries.put(path, entry.getValue());
            }
        }
    }

    /**
     * Validates one flat overlay entry against the catalog and records a finding when it is unknown or mistyped.
     *
     * @param path dotted configuration key.
     * @param value parsed scalar value.
     * @param findings accumulating findings.
     */
    private void validateEntry(String path, Object value, List<StaticConfigFinding> findings) {
        String expectedType = typesByKey.get(path);
        if (expectedType == null) {
            findings.add(new StaticConfigFinding(path, String.valueOf(value), StaticConfigFinding.ISSUE_UNKNOWN_KEY,
                    "Key is not in the verified Artemis configuration key catalog (catalog " + catalog.catalogVersion() + ", Artemis commit "
                            + catalog.verifiedAgainstArtemisCommit() + ")."));
            return;
        }
        if (isDeploymentTimePlaceholder(value) || hasAcceptableType(expectedType, value)) {
            return;
        }
        findings.add(new StaticConfigFinding(path, String.valueOf(value), StaticConfigFinding.ISSUE_TYPE_MISMATCH,
                "Expected type '" + expectedType + "' but found value '" + value + "' of type " + describeActualType(value) + "."));
    }

    /**
     * Checks whether a value is exactly one environment placeholder, which passes every type check because it is
     * resolved by the deployment environment.
     *
     * @param value parsed scalar value.
     * @return true if the value is a single {@code ${VARIABLE}} placeholder.
     */
    private boolean isDeploymentTimePlaceholder(Object value) {
        return value instanceof String text && ENV_PLACEHOLDER_PATTERN.matcher(text).matches();
    }

    /**
     * Checks whether a value satisfies the catalog type of its key.
     *
     * @param expectedType catalog type of the key.
     * @param value parsed scalar value.
     * @return true if the value has the expected type.
     */
    private boolean hasAcceptableType(String expectedType, Object value) {
        return switch (expectedType) {
            case ArtemisConfigKeyCatalog.TYPE_BOOLEAN -> value instanceof Boolean;
            case ArtemisConfigKeyCatalog.TYPE_STRING -> value instanceof String;
            case ArtemisConfigKeyCatalog.TYPE_URL -> value instanceof String text && isAbsoluteHttpUrl(text);
            default -> false;
        };
    }

    /**
     * Checks whether a string is an absolute http or https URL with a host.
     *
     * @param text string value.
     * @return true if the string parses as an absolute http(s) URL.
     */
    private boolean isAbsoluteHttpUrl(String text) {
        try {
            URI uri = new URI(text);
            boolean httpScheme = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
            return httpScheme && uri.getHost() != null;
        }
        catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Describes the parsed type of a value for a finding message.
     *
     * @param value parsed scalar value.
     * @return short type description such as {@code boolean} or {@code string}.
     */
    private String describeActualType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Integer || value instanceof Long) {
            return "integer";
        }
        if (value instanceof Number) {
            return "decimal";
        }
        if (value instanceof String) {
            return "string";
        }
        return value.getClass().getSimpleName();
    }

    /**
     * Loads and parses the configured catalog resource.
     *
     * @param resourceLoader Spring resource loader.
     * @param objectMapper Jackson mapper.
     * @param catalogLocation catalog resource location.
     * @return parsed catalog.
     * @throws FeatureModelLoadException if the resource cannot be read or parsed.
     */
    private ArtemisConfigKeyCatalog loadCatalog(ResourceLoader resourceLoader, ObjectMapper objectMapper, String catalogLocation) {
        Resource resource = resourceLoader.getResource(catalogLocation);
        try (InputStream inputStream = resource.getInputStream()) {
            ArtemisConfigKeyCatalog loaded = objectMapper.readValue(inputStream, ArtemisConfigKeyCatalog.class);
            log.info("Loaded Artemis config key catalog {} with {} keys, verified against Artemis commit {}, from {}.", loaded.catalogVersion(),
                    loaded.keys().size(), loaded.verifiedAgainstArtemisCommit(), catalogLocation);
            return loaded;
        }
        catch (IOException e) {
            log.error("Could not load the Artemis config key catalog from {}.", catalogLocation, e);
            throw new FeatureModelLoadException("Could not load the Artemis config key catalog from " + catalogLocation + ".", e);
        }
    }
}
