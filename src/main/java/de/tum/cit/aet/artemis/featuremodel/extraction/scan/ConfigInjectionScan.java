package de.tum.cit.aet.artemis.featuremodel.extraction.scan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigInjection;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigInjection.ConfigurationPropertiesPrefix;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.JavaSourceParser;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.SourceScanResult;

/**
 * Scans production Java sources for guarded configuration injection sites: {@code @Value("${...}")} placeholder keys
 * and {@code @ConfigurationProperties} prefixes, together with the file's {@code @Conditional(*Enabled.class)} and
 * {@code @Profile} guards. Only files containing one of the two injection markers are parsed, so the structural pass
 * stays proportional to the configuration surface instead of the whole source tree. A single unparseable file is
 * reported and skipped.
 */
class ConfigInjectionScan {

    /** Textual marker of a placeholder injection; files without either marker are never parsed. */
    private static final String VALUE_PLACEHOLDER_MARKER = "@" + ArtemisSourceConventions.Symbols.VALUE_ANNOTATION + "(\"${";

    private static final String CONFIGURATION_PROPERTIES_MARKER = "@" + ArtemisSourceConventions.Symbols.CONFIGURATION_PROPERTIES_ANNOTATION;

    /** One {@code ${key}} or {@code ${key:default}} placeholder inside an injected value expression. */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}:{]+)(?::[^{}]*)?}");

    /**
     * Scans the production Java sources of the given checkout.
     *
     * @param source Artemis source repository.
     * @return per-file injection facts sorted by file, and isolated parse errors.
     * @throws IOException if the source tree cannot be traversed.
     */
    SourceScanResult<List<ExtractedConfigInjection>> scan(ArtemisSourceRepository source) throws IOException {
        List<ExtractedConfigInjection> injections = new ArrayList<>();
        List<ReportItem> errors = new ArrayList<>();
        for (String file : source.findFiles(ArtemisSourceConventions.Roots.JAVA, ArtemisSourceConventions.Naming.JAVA_SUFFIX)) {
            try {
                String content = source.readFile(file);
                if (!content.contains(VALUE_PLACEHOLDER_MARKER) && !content.contains(CONFIGURATION_PROPERTIES_MARKER)) {
                    continue;
                }
                scanFile(content, file).ifPresent(injections::add);
            }
            catch (IOException | RuntimeException e) {
                errors.add(ReportItem.error(ReportItem.CODE_EXTRACTOR_ERROR, file, "Could not parse configuration injection sites: " + e.getMessage()));
            }
        }
        injections.sort(Comparator.comparing(ExtractedConfigInjection::file));
        return SourceScanResult.withDiagnostics(List.copyOf(injections), errors);
    }

    /**
     * Parses one file and collects its injected keys, declared prefixes, and guards.
     *
     * @param content Java source text.
     * @param file checkout-relative path.
     * @return injection facts, or empty when the markers only appeared outside recognizable annotations.
     * @throws IllegalArgumentException if the file cannot be parsed.
     */
    private Optional<ExtractedConfigInjection> scanFile(String content, String file) {
        CompilationUnit unit = JavaSourceParser.parse(content, file);
        TreeSet<String> valueKeys = new TreeSet<>();
        TreeSet<String> conditionGuards = new TreeSet<>();
        TreeSet<String> profileGuards = new TreeSet<>();
        List<ConfigurationPropertiesPrefix> prefixes = new ArrayList<>();
        for (AnnotationExpr annotation : unit.findAll(AnnotationExpr.class)) {
            switch (annotation.getName().getIdentifier()) {
                case ArtemisSourceConventions.Symbols.VALUE_ANNOTATION -> collectPlaceholderKeys(annotation, valueKeys);
                case ArtemisSourceConventions.Symbols.CONFIGURATION_PROPERTIES_ANNOTATION -> collectPrefix(annotation, prefixes);
                case ArtemisSourceConventions.Symbols.CONDITIONAL_ANNOTATION -> collectConditionGuards(annotation, conditionGuards);
                case ArtemisSourceConventions.Symbols.PROFILE_ANNOTATION -> collectProfileGuards(annotation, profileGuards);
                default -> {
                }
            }
        }
        if (valueKeys.isEmpty() && prefixes.isEmpty()) {
            return Optional.empty();
        }
        List<ConfigurationPropertiesPrefix> sortedPrefixes = prefixes.stream().distinct()
                .sorted(Comparator.comparing(ConfigurationPropertiesPrefix::prefix).thenComparing(ConfigurationPropertiesPrefix::declaringType)).toList();
        String packageName = unit.getPackageDeclaration().map(declaration -> declaration.getNameAsString()).orElse("");
        return Optional.of(new ExtractedConfigInjection(file, packageName, List.copyOf(conditionGuards), List.copyOf(profileGuards),
                List.copyOf(valueKeys), sortedPrefixes));
    }

    /**
     * Collects the placeholder keys of one {@code @Value} annotation, stripping default values after the colon.
     *
     * @param annotation parsed annotation.
     * @param valueKeys key sink.
     */
    private void collectPlaceholderKeys(AnnotationExpr annotation, TreeSet<String> valueKeys) {
        annotationValue(annotation).ifPresent(value -> {
            if (value instanceof StringLiteralExpr literal) {
                Matcher matcher = PLACEHOLDER_PATTERN.matcher(literal.getValue());
                while (matcher.find()) {
                    valueKeys.add(matcher.group(1).strip());
                }
            }
        });
    }

    /**
     * Collects the prefix of one {@code @ConfigurationProperties} annotation, from the {@code prefix} attribute or
     * the single-member value, together with the nearest enclosing type declaration.
     *
     * @param annotation parsed annotation.
     * @param prefixes prefix sink.
     */
    private void collectPrefix(AnnotationExpr annotation, List<ConfigurationPropertiesPrefix> prefixes) {
        Expression value = switch (annotation) {
            case SingleMemberAnnotationExpr single -> single.getMemberValue();
            case NormalAnnotationExpr normal -> normal.getPairs().stream()
                    .filter(pair -> "prefix".equals(pair.getNameAsString()) || "value".equals(pair.getNameAsString())).map(pair -> pair.getValue()).findFirst()
                    .orElse(null);
            default -> null;
        };
        if (value instanceof StringLiteralExpr literal && !literal.getValue().isBlank()) {
            prefixes.add(new ConfigurationPropertiesPrefix(literal.getValue(), declaringTypeOf(annotation)));
        }
    }

    /**
     * Collects the {@code *Enabled} condition class references of one {@code @Conditional} annotation.
     *
     * @param annotation parsed annotation.
     * @param conditionGuards guard sink.
     */
    private void collectConditionGuards(AnnotationExpr annotation, TreeSet<String> conditionGuards) {
        for (ClassExpr reference : annotation.findAll(ClassExpr.class)) {
            String name = reference.getType().asString();
            String simpleName = name.substring(name.lastIndexOf('.') + 1);
            if (simpleName.endsWith(ArtemisSourceConventions.Naming.CONDITION_CLASS_SUFFIX)) {
                conditionGuards.add(simpleName);
            }
        }
    }

    /**
     * Collects the profile expressions of one {@code @Profile} annotation: string literal values and referenced
     * constant names.
     *
     * @param annotation parsed annotation.
     * @param profileGuards guard sink.
     */
    private void collectProfileGuards(AnnotationExpr annotation, TreeSet<String> profileGuards) {
        for (StringLiteralExpr literal : annotation.findAll(StringLiteralExpr.class)) {
            profileGuards.add(literal.getValue());
        }
        for (FieldAccessExpr constant : annotation.findAll(FieldAccessExpr.class)) {
            profileGuards.add(constant.getNameAsString());
        }
        for (NameExpr reference : annotation.findAll(NameExpr.class)) {
            if (reference.getParentNode().filter(parent -> parent instanceof FieldAccessExpr).isEmpty()) {
                profileGuards.add(reference.getNameAsString());
            }
        }
    }

    /**
     * Resolves the value expression of a one-attribute annotation such as {@code @Value}.
     *
     * @param annotation parsed annotation.
     * @return value expression, or empty for marker or multi-attribute shapes.
     */
    private Optional<Expression> annotationValue(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            return Optional.of(single.getMemberValue());
        }
        if (annotation instanceof NormalAnnotationExpr normal) {
            return normal.getPairs().stream().filter(pair -> "value".equals(pair.getNameAsString())).map(pair -> (Expression) pair.getValue()).findFirst();
        }
        return Optional.empty();
    }

    /**
     * Names the nearest type declaration enclosing an annotation.
     *
     * @param annotation parsed annotation.
     * @return simple name of the enclosing type, or an empty string for a file-level annotation.
     */
    private String declaringTypeOf(AnnotationExpr annotation) {
        Node current = annotation.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof TypeDeclaration<?> type) {
                return type.getNameAsString();
            }
            current = current.getParentNode().orElse(null);
        }
        return "";
    }
}
