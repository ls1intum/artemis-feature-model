package de.tum.cit.aet.artemis.featuremodel.extraction.scan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedAnnotation;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedAnnotationSemantics;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedAnnotationSemantics.ConfigurationDeclaration;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.JavaSourceParser;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.SourceScanResult;

/** Reads {@code @ArtemisFeature} contract v2 from Java source without loading annotated classes. */
class ArtemisFeatureAnnotationScan {

    private static final Set<String> ATTRIBUTE_NAMES = Set.of("id", "configuration");

    /** Attributes of contract v1 whose values moved into the manifest {@code features} section. */
    private static final Set<String> RETIRED_ATTRIBUTES = Set.of("group", "parent", "kind", "requiresCapabilities", "providesCapabilities", "name", "description",
            "documentationUrl");

    private static final Set<String> CONFIG_ATTRIBUTE_NAMES = Set.of("key", "secret");

    /**
     * Scans Java files containing the annotation marker.
     *
     * @param source Artemis source repository.
     * @return parsed annotations and isolated parse errors.
     * @throws IOException if the source tree cannot be traversed.
     */
    SourceScanResult<List<ExtractedAnnotation>> scan(ArtemisSourceRepository source) throws IOException {
        List<ExtractedAnnotation> annotations = new ArrayList<>();
        List<ReportItem> errors = new ArrayList<>();
        for (String file : source.findFiles(ArtemisSourceConventions.Roots.JAVA, ArtemisSourceConventions.Naming.JAVA_SUFFIX)) {
            String content;
            try {
                content = source.readFile(file);
                if (!content.contains("@" + ArtemisSourceConventions.Symbols.ARTEMIS_FEATURE_ANNOTATION)) {
                    continue;
                }
                scanFile(content, file, annotations);
            }
            catch (IOException | RuntimeException e) {
                errors.add(ReportItem.error(ReportItem.CODE_EXTRACTOR_ERROR, file, "Could not parse ArtemisFeature annotation: " + e.getMessage()));
            }
        }
        annotations.sort(Comparator.comparing(ExtractedAnnotation::anchor).thenComparing(ExtractedAnnotation::file)
                .thenComparing(ExtractedAnnotation::line, Comparator.nullsLast(Integer::compareTo)));
        return SourceScanResult.withDiagnostics(List.copyOf(annotations), errors);
    }

    /**
     * Parses one file and collects every annotated type, field, and enum constant anchor.
     *
     * @param content Java source text.
     * @param file checkout-relative path.
     * @param annotations annotation sink.
     * @throws IllegalArgumentException if the file or an annotation shape cannot be parsed.
     */
    private void scanFile(String content, String file, List<ExtractedAnnotation> annotations) {
        CompilationUnit unit = JavaSourceParser.parse(content, file);
        String packageName = unit.getPackageDeclaration().map(declaration -> declaration.getNameAsString()).orElse("");
        for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
            findAnnotation(type).ifPresent(annotation -> {
                String anchor = packageName.isEmpty() ? type.getNameAsString() : packageName + "." + type.getNameAsString();
                annotations.add(new ExtractedAnnotation(anchor, parseSemantics(annotation, file), file, JavaSourceParser.lineOf(type)));
            });
        }
        for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
            findAnnotation(field).ifPresent(annotation -> field.getVariables().forEach(variable -> annotations
                    .add(new ExtractedAnnotation(variable.getNameAsString(), parseSemantics(annotation, file), file, JavaSourceParser.lineOf(variable)))));
        }
        for (EnumConstantDeclaration constant : unit.findAll(EnumConstantDeclaration.class)) {
            findAnnotation(constant).ifPresent(annotation -> annotations.add(new ExtractedAnnotation("toggle:" + constant.getNameAsString(), parseSemantics(annotation, file),
                    file, JavaSourceParser.lineOf(constant))));
        }
    }

    /**
     * Finds the feature annotation on a node.
     *
     * @param node annotatable parsed node.
     * @return the feature annotation, or empty.
     */
    private Optional<AnnotationExpr> findAnnotation(NodeWithAnnotations<?> node) {
        return node.getAnnotations().stream()
                .filter(annotation -> ArtemisSourceConventions.Symbols.ARTEMIS_FEATURE_ANNOTATION.equals(annotation.getName().getIdentifier())).findFirst();
    }

    /**
     * Parses the attribute values of one feature annotation. A retired contract-v1 attribute is rejected with a
     * migration message naming the manifest section that owns the value now.
     *
     * @param annotation parsed annotation expression.
     * @param file checkout-relative path, used in failure messages.
     * @return parsed semantics.
     * @throws IllegalArgumentException if the annotation uses an unsupported shape, a retired attribute, or an
     *             unknown attribute.
     */
    private ExtractedAnnotationSemantics parseSemantics(AnnotationExpr annotation, String file) {
        if (!(annotation instanceof NormalAnnotationExpr normal)) {
            throw new IllegalArgumentException("@ArtemisFeature in " + file + " must use named attributes.");
        }
        List<String> retiredAttributes = normal.getPairs().stream().map(pair -> pair.getNameAsString()).filter(RETIRED_ATTRIBUTES::contains).toList();
        if (!retiredAttributes.isEmpty()) {
            throw new IllegalArgumentException("@ArtemisFeature in " + file + " declares the retired attribute(s) " + retiredAttributes
                    + "; since contract v2 the annotation carries only 'id' and 'configuration'. Declare these values in the manifest 'features' entry of the feature id.");
        }
        List<String> unknownAttributes = normal.getPairs().stream().map(pair -> pair.getNameAsString()).filter(name -> !ATTRIBUTE_NAMES.contains(name)).toList();
        if (!unknownAttributes.isEmpty()) {
            throw new IllegalArgumentException("@ArtemisFeature in " + file + " contains unknown attribute(s): " + unknownAttributes + ".");
        }
        String id = requiredString(normal, "id", file);
        return new ExtractedAnnotationSemantics(id, parseConfiguration(attribute(normal, "configuration"), file));
    }

    /**
     * Parses the nested {@code @ArtemisFeatureConfig} declarations, accepting a single declaration as a one-element
     * array.
     *
     * @param value value expression of the configuration attribute, or null when absent.
     * @param file checkout-relative path, used in failure messages.
     * @return declared configuration keys in declaration order.
     * @throws IllegalArgumentException if a declaration is not a well-formed {@code @ArtemisFeatureConfig}.
     */
    private List<ConfigurationDeclaration> parseConfiguration(Expression value, String file) {
        if (value == null) {
            return List.of();
        }
        List<Expression> values = value instanceof ArrayInitializerExpr array ? array.getValues() : List.of(value);
        List<ConfigurationDeclaration> declarations = new ArrayList<>();
        for (Expression item : values) {
            if (!(item instanceof NormalAnnotationExpr config)
                    || !ArtemisSourceConventions.Symbols.ARTEMIS_FEATURE_CONFIG_ANNOTATION.equals(config.getName().getIdentifier())) {
                throw new IllegalArgumentException("@ArtemisFeature attribute 'configuration' in " + file + " must contain @ArtemisFeatureConfig declarations with named attributes.");
            }
            List<String> unknownAttributes = config.getPairs().stream().map(pair -> pair.getNameAsString()).filter(name -> !CONFIG_ATTRIBUTE_NAMES.contains(name)).toList();
            if (!unknownAttributes.isEmpty()) {
                throw new IllegalArgumentException("@ArtemisFeatureConfig in " + file + " contains unknown attribute(s): " + unknownAttributes + ".");
            }
            declarations.add(new ConfigurationDeclaration(requiredString(config, "key", file), optionalBoolean(config, "secret", file)));
        }
        return List.copyOf(declarations);
    }

    /**
     * Reads a required non-blank string attribute.
     *
     * @param annotation parsed annotation.
     * @param name attribute name.
     * @param file checkout-relative path, used in failure messages.
     * @return attribute value.
     * @throws IllegalArgumentException if the attribute is absent, blank, or not a string literal.
     */
    private String requiredString(NormalAnnotationExpr annotation, String name, String file) {
        Expression value = attribute(annotation, name);
        if (!(value instanceof StringLiteralExpr literal) || literal.getValue().isBlank()) {
            throw new IllegalArgumentException("@" + annotation.getName().getIdentifier() + " in " + file + " requires a non-blank string literal '" + name + "' attribute.");
        }
        return literal.getValue();
    }

    /**
     * Reads an optional boolean attribute.
     *
     * @param annotation parsed annotation.
     * @param name attribute name.
     * @param file checkout-relative path, used in failure messages.
     * @return attribute value, or false when absent.
     * @throws IllegalArgumentException if the attribute is present but not a boolean literal.
     */
    private boolean optionalBoolean(NormalAnnotationExpr annotation, String name, String file) {
        Expression value = attribute(annotation, name);
        if (value == null) {
            return false;
        }
        if (!(value instanceof BooleanLiteralExpr literal)) {
            throw new IllegalArgumentException("@" + annotation.getName().getIdentifier() + " attribute '" + name + "' in " + file + " must be a boolean literal.");
        }
        return literal.getValue();
    }

    /**
     * Finds the value expression of a named annotation attribute.
     *
     * @param annotation parsed annotation.
     * @param name attribute name.
     * @return value expression, or null when the attribute is absent.
     */
    private Expression attribute(NormalAnnotationExpr annotation, String name) {
        return annotation.getPairs().stream().filter(pair -> name.equals(pair.getNameAsString())).map(pair -> pair.getValue()).findFirst().orElse(null);
    }
}
