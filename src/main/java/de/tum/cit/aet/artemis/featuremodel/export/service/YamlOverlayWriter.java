package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.featuremodel.export.domain.OverlayEntry;

/**
 * Deterministic YAML writer for the generated configuration overlay.
 *
 * <p>
 * Dotted overlay paths are expanded into a nested block structure, preserving insertion order and hyphenated key names.
 * Booleans, integers, and decimals are written as YAML scalars of the matching type; strings are written plain when
 * safe and double-quoted only when a plain scalar would be ambiguous. Environment placeholders such as
 * {@code ${ARTEMIS_IRIS_SECRET_TOKEN}} are carried as plain strings and emitted unquoted so Spring can resolve them.
 */
@Component
public class YamlOverlayWriter {

    private static final String INDENT = "  ";

    private static final String LEADING_SPECIAL = "!&*?|>%@`\"'#,[]{}:";

    /**
     * Writes overlay entries as a nested YAML document.
     *
     * @param entries ordered overlay entries.
     * @return YAML document text, empty when there are no entries.
     */
    public String write(List<OverlayEntry> entries) {
        Map<String, Object> root = new LinkedHashMap<>();
        for (OverlayEntry entry : entries) {
            insert(root, entry.path(), entry.value());
        }
        StringBuilder builder = new StringBuilder();
        emit(root, 0, builder);
        return builder.toString();
    }

    /**
     * Inserts a single dotted path and value into the nested map, creating intermediate maps as needed.
     *
     * @param root nested map being built.
     * @param path dotted configuration path.
     * @param value typed scalar value.
     * @throws IllegalStateException if a path conflicts with an existing scalar or branch.
     */
    @SuppressWarnings("unchecked")
    private void insert(Map<String, Object> root, String path, Object value) {
        String[] segments = path.split("\\.");
        Map<String, Object> current = root;
        for (int index = 0; index < segments.length - 1; index++) {
            Object child = current.computeIfAbsent(segments[index], key -> new LinkedHashMap<String, Object>());
            if (!(child instanceof Map)) {
                throw new IllegalStateException("Overlay path '" + path + "' conflicts with a scalar at '" + segments[index] + "'.");
            }
            current = (Map<String, Object>) child;
        }
        String leaf = segments[segments.length - 1];
        if (current.get(leaf) instanceof Map) {
            throw new IllegalStateException("Overlay path '" + path + "' conflicts with an existing branch.");
        }
        current.put(leaf, value);
    }

    /**
     * Emits a nested map as indented YAML.
     *
     * @param map nested map to emit.
     * @param depth current indentation depth.
     * @param builder output accumulator.
     */
    @SuppressWarnings("unchecked")
    private void emit(Map<String, Object> map, int depth, StringBuilder builder) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            builder.append(INDENT.repeat(depth)).append(entry.getKey()).append(":");
            Object value = entry.getValue();
            if (value instanceof Map) {
                builder.append("\n");
                emit((Map<String, Object>) value, depth + 1, builder);
            }
            else {
                builder.append(" ").append(formatScalar(value)).append("\n");
            }
        }
    }

    /**
     * Formats a scalar value for YAML output.
     *
     * @param value scalar value (Boolean, Long, Double, or String).
     * @return YAML scalar text.
     */
    private String formatScalar(Object value) {
        if (value instanceof Boolean || value instanceof Long || value instanceof Integer || value instanceof Double) {
            return value.toString();
        }
        String text = String.valueOf(value);
        return needsQuoting(text) ? quote(text) : text;
    }

    /**
     * Decides whether a string scalar must be double-quoted to remain a valid, unambiguous YAML plain scalar.
     *
     * @param value string scalar.
     * @return true if the value must be quoted.
     */
    private boolean needsQuoting(String value) {
        if (value.isEmpty()) {
            return true;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if (Character.isWhitespace(first) || Character.isWhitespace(last)) {
            return true;
        }
        if (LEADING_SPECIAL.indexOf(first) >= 0) {
            return true;
        }
        return value.contains(": ") || value.endsWith(":") || value.contains(" #") || value.indexOf('\n') >= 0 || value.indexOf('\t') >= 0;
    }

    /**
     * Double-quotes a string scalar, escaping backslashes, quotes, and control whitespace.
     *
     * @param value string scalar.
     * @return double-quoted YAML scalar.
     */
    private String quote(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }
}
