package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;

/** Reads {@code @ArtemisFeature} semantics from Java source without loading annotated classes. */
class ArtemisFeatureAnnotationScan {

    private static final String JAVA_SOURCE_ROOT = "src/main/java";

    private static final String ANNOTATION_NAME = "ArtemisFeature";

    private static final Set<String> ATTRIBUTE_NAMES = Set.of("id", "group", "parent", "kind", "requiresCapabilities", "providesCapabilities", "name", "description",
            "documentationUrl");

    /**
     * Parsed annotation values. Null optional values mean that the attribute was not explicitly present and therefore
     * must not override manifest semantics.
     *
     * @param id required curated id.
     * @param group group override.
     * @param parent parent override.
     * @param kind kind override.
     * @param requiresCapabilities required capabilities override.
     * @param providesCapabilities provided capabilities override.
     * @param name name override.
     * @param description description override.
     * @param documentationUrl documentation URL override.
     */
    record AnnotationSemantics(String id, String group, String parent, String kind, List<String> requiresCapabilities, List<String> providesCapabilities, String name,
            String description, String documentationUrl) {
    }

    /**
     * One annotated source anchor.
     *
     * @param anchor fully-qualified type, field symbol, or namespaced enum-member anchor.
     * @param semantics parsed values.
     * @param file checkout-relative source file.
     * @param line annotation target line.
     */
    record AnnotatedAnchor(String anchor, AnnotationSemantics semantics, String file, Integer line) {
    }

    /**
     * Annotation scan output.
     *
     * @param annotations annotations sorted by anchor, file, and line.
     * @param errors per-file annotation parse failures.
     */
    record Result(List<AnnotatedAnchor> annotations, List<ReportItem> errors) {

        /**
         * Creates an empty scan result.
         *
         * @return empty result.
         */
        static Result empty() {
            return new Result(List.of(), List.of());
        }
    }

    /**
     * Scans Java files containing the annotation marker.
     *
     * @param source Artemis source repository.
     * @return parsed annotations and isolated parse errors.
     * @throws IOException if the source tree cannot be traversed.
     */
    Result scan(ArtemisSourceRepository source) throws IOException {
        List<AnnotatedAnchor> annotations = new ArrayList<>();
        List<ReportItem> errors = new ArrayList<>();
        for (String file : source.findFiles(JAVA_SOURCE_ROOT, ".java")) {
            String content;
            try {
                content = source.readFile(file);
                if (!content.contains("@" + ANNOTATION_NAME)) {
                    continue;
                }
                scanFile(content, file, annotations);
            }
            catch (IOException | RuntimeException e) {
                errors.add(ReportItem.error(ReportItem.CODE_EXTRACTOR_ERROR, file, "Could not parse ArtemisFeature annotation: " + e.getMessage()));
            }
        }
        annotations.sort(Comparator.comparing(AnnotatedAnchor::anchor).thenComparing(AnnotatedAnchor::file)
                .thenComparing(AnnotatedAnchor::line, Comparator.nullsLast(Integer::compareTo)));
        return new Result(List.copyOf(annotations), List.copyOf(errors));
    }

    private void scanFile(String content, String file, List<AnnotatedAnchor> annotations) {
        CompilationUnit unit = JavaSourceParser.parse(content, file);
        String packageName = unit.getPackageDeclaration().map(declaration -> declaration.getNameAsString()).orElse("");
        for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
            findAnnotation(type).ifPresent(annotation -> {
                String anchor = packageName.isEmpty() ? type.getNameAsString() : packageName + "." + type.getNameAsString();
                annotations.add(new AnnotatedAnchor(anchor, parseSemantics(annotation, file), file, JavaSourceParser.lineOf(type)));
            });
        }
        for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
            findAnnotation(field).ifPresent(annotation -> field.getVariables().forEach(variable -> annotations
                    .add(new AnnotatedAnchor(variable.getNameAsString(), parseSemantics(annotation, file), file, JavaSourceParser.lineOf(variable)))));
        }
        for (EnumConstantDeclaration constant : unit.findAll(EnumConstantDeclaration.class)) {
            findAnnotation(constant).ifPresent(annotation -> annotations.add(new AnnotatedAnchor("toggle:" + constant.getNameAsString(), parseSemantics(annotation, file),
                    file, JavaSourceParser.lineOf(constant))));
        }
    }

    private java.util.Optional<AnnotationExpr> findAnnotation(NodeWithAnnotations<?> node) {
        return node.getAnnotations().stream().filter(annotation -> ANNOTATION_NAME.equals(annotation.getName().getIdentifier())).findFirst();
    }

    private AnnotationSemantics parseSemantics(AnnotationExpr annotation, String file) {
        if (!(annotation instanceof NormalAnnotationExpr normal)) {
            throw new IllegalArgumentException("@ArtemisFeature in " + file + " must use named attributes.");
        }
        List<String> unknownAttributes = normal.getPairs().stream().map(pair -> pair.getNameAsString()).filter(name -> !ATTRIBUTE_NAMES.contains(name)).toList();
        if (!unknownAttributes.isEmpty()) {
            throw new IllegalArgumentException("@ArtemisFeature in " + file + " contains unknown attribute(s): " + unknownAttributes + ".");
        }
        String id = requiredString(normal, "id", file);
        return new AnnotationSemantics(id, optionalString(normal, "group", file), optionalString(normal, "parent", file), optionalString(normal, "kind", file),
                optionalStringList(normal, "requiresCapabilities", file), optionalStringList(normal, "providesCapabilities", file), optionalString(normal, "name", file),
                optionalString(normal, "description", file), optionalString(normal, "documentationUrl", file));
    }

    private String requiredString(NormalAnnotationExpr annotation, String name, String file) {
        String value = optionalString(annotation, name, file);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("@ArtemisFeature in " + file + " requires a non-blank '" + name + "' attribute.");
        }
        return value;
    }

    private String optionalString(NormalAnnotationExpr annotation, String name, String file) {
        Expression value = attribute(annotation, name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof StringLiteralExpr literal)) {
            throw new IllegalArgumentException("@ArtemisFeature attribute '" + name + "' in " + file + " must be a string literal.");
        }
        return literal.getValue().isEmpty() ? null : literal.getValue();
    }

    private List<String> optionalStringList(NormalAnnotationExpr annotation, String name, String file) {
        Expression value = attribute(annotation, name);
        if (value == null) {
            return null;
        }
        List<Expression> values = value instanceof ArrayInitializerExpr array ? array.getValues() : List.of(value);
        List<String> strings = new ArrayList<>();
        for (Expression item : values) {
            if (!(item instanceof StringLiteralExpr literal) || literal.getValue().isBlank()) {
                throw new IllegalArgumentException("@ArtemisFeature attribute '" + name + "' in " + file + " must contain string literals.");
            }
            strings.add(literal.getValue());
        }
        return List.copyOf(strings);
    }

    private Expression attribute(NormalAnnotationExpr annotation, String name) {
        return annotation.getPairs().stream().filter(pair -> name.equals(pair.getNameAsString())).map(pair -> pair.getValue()).findFirst().orElse(null);
    }
}
