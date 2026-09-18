package de.tum.cit.aet.artemis.featuremodel.extraction.scan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedFeatureUsage;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedFeatureUsage.MethodLabel;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.JavaSourceParser;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.SourceScanResult;

/**
 * Scans production Java sources for {@code @FeatureUsage} placements: the class-level label and the method-level
 * overrides of every annotated type, together with the type's {@code @Conditional(*Enabled.class)} and {@code @Profile}
 * guards as written in source. Only files that lexically contain the annotation are parsed. A label outside the
 * {@code area/feature} kebab-case shape is reported as a warning and kept; a single unparseable file is reported and
 * skipped. Nothing here decides which model member a usage belongs to — that join is the model stage's.
 */
class FeatureUsageScan {

    /** Textual marker of a placement; files without it are never parsed. */
    private static final String FEATURE_USAGE_MARKER = "@" + ArtemisSourceConventions.Symbols.FEATURE_USAGE_ANNOTATION;

    /** Kebab-case {@code area/feature} label shape the admin feature usage page splits on. */
    static final Pattern LABEL_PATTERN = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*/[a-z0-9]+(-[a-z0-9]+)*");

    private static final String ARTEMIS_PACKAGE_PREFIX = "de.tum.cit.aet.artemis.";

    /**
     * Scans the production Java sources of the given checkout.
     *
     * @param source Artemis source repository.
     * @return per-type placements sorted by file and type, with malformed-label warnings and isolated parse errors.
     * @throws IOException if the source tree cannot be traversed.
     */
    SourceScanResult<List<ExtractedFeatureUsage>> scan(ArtemisSourceRepository source) throws IOException {
        List<ExtractedFeatureUsage> usages = new ArrayList<>();
        List<ReportItem> diagnostics = new ArrayList<>();
        for (String file : source.findFiles(ArtemisSourceConventions.Roots.JAVA, ArtemisSourceConventions.Naming.JAVA_SUFFIX)) {
            try {
                String content = source.readFile(file);
                if (!content.contains(FEATURE_USAGE_MARKER)) {
                    continue;
                }
                scanFile(content, file, usages, diagnostics);
            }
            catch (IOException | RuntimeException e) {
                diagnostics.add(ReportItem.error(ReportItem.CODE_EXTRACTOR_ERROR, file, "Could not parse feature usage placements: " + e.getMessage()));
            }
        }
        usages.sort(Comparator.comparing(ExtractedFeatureUsage::file).thenComparing(ExtractedFeatureUsage::type));
        return SourceScanResult.withDiagnostics(List.copyOf(usages), diagnostics);
    }

    /**
     * Parses one file and records every type carrying a class-level or method-level placement.
     *
     * @param content Java source text.
     * @param file checkout-relative path.
     * @param usages placement sink.
     * @param diagnostics diagnostic sink for malformed labels.
     * @throws IllegalArgumentException if the file cannot be parsed.
     */
    private void scanFile(String content, String file, List<ExtractedFeatureUsage> usages, List<ReportItem> diagnostics) {
        CompilationUnit unit = JavaSourceParser.parse(content, file);
        String module = moduleOf(unit.getPackageDeclaration().map(declaration -> declaration.getNameAsString()).orElse(""));
        for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
            String classLabel = label(type, file, diagnostics).orElse(null);
            List<MethodLabel> methodLabels = new ArrayList<>();
            for (MethodDeclaration method : type.getMethods()) {
                label(method, file, diagnostics).ifPresent(label -> methodLabels.add(new MethodLabel(method.getNameAsString(), label, lineOf(method))));
            }
            if (classLabel == null && methodLabels.isEmpty()) {
                continue;
            }
            methodLabels.sort(Comparator.comparing(MethodLabel::method).thenComparing(MethodLabel::label).thenComparing(MethodLabel::line));
            TreeSet<String> conditionGuards = new TreeSet<>();
            TreeSet<String> profileGuards = new TreeSet<>();
            for (AnnotationExpr annotation : type.getAnnotations()) {
                switch (annotation.getName().getIdentifier()) {
                    case ArtemisSourceConventions.Symbols.CONDITIONAL_ANNOTATION -> collectConditionGuards(annotation, conditionGuards);
                    case ArtemisSourceConventions.Symbols.PROFILE_ANNOTATION -> collectProfileGuards(annotation, profileGuards);
                    default -> {
                    }
                }
            }
            usages.add(new ExtractedFeatureUsage(file, lineOf(type), module, type.getNameAsString(), classLabel, methodLabels, List.copyOf(conditionGuards),
                    List.copyOf(profileGuards)));
        }
    }

    /**
     * Reads the {@code @FeatureUsage} label of a type or method, reporting a label outside the kebab-case shape.
     *
     * @param node annotated type or method.
     * @param file checkout-relative path for the warning.
     * @param diagnostics diagnostic sink.
     * @return declared label, or empty when the node carries no placement or a non-literal value.
     */
    private Optional<String> label(NodeWithAnnotations<?> node, String file, List<ReportItem> diagnostics) {
        for (AnnotationExpr annotation : node.getAnnotations()) {
            if (!ArtemisSourceConventions.Symbols.FEATURE_USAGE_ANNOTATION.equals(annotation.getName().getIdentifier())) {
                continue;
            }
            Optional<String> label = annotationValue(annotation).filter(StringLiteralExpr.class::isInstance).map(value -> ((StringLiteralExpr) value).getValue());
            label.filter(value -> !LABEL_PATTERN.matcher(value).matches()).ifPresent(value -> diagnostics.add(ReportItem.warning(
                    ReportItem.CODE_FEATURE_USAGE_LABEL_MALFORMED, file + ":" + annotation.getBegin().map(position -> position.line).orElse(0),
                    "@FeatureUsage label '" + value + "' is not an area/feature kebab-case label with exactly one slash; the placement is kept.")));
            return label;
        }
        return Optional.empty();
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
     * constant names, as written.
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
     * Resolves the value expression of a one-attribute annotation.
     *
     * @param annotation parsed annotation.
     * @return value expression, or empty for marker or multi-attribute shapes without a value pair.
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
     * Derives the Artemis module of a file from its package: the first segment below the Artemis root package, or
     * the whole package for files outside it.
     *
     * @param packageName declared package.
     * @return module name.
     */
    private String moduleOf(String packageName) {
        if (!packageName.startsWith(ARTEMIS_PACKAGE_PREFIX)) {
            return packageName;
        }
        String below = packageName.substring(ARTEMIS_PACKAGE_PREFIX.length());
        int dot = below.indexOf('.');
        return dot < 0 ? below : below.substring(0, dot);
    }

    /**
     * Reads the declaration line of a node, preferring the name token so leading annotations do not shift it.
     *
     * @param node type or method declaration.
     * @return 1-based line, or null when the parser recorded no position.
     */
    private Integer lineOf(com.github.javaparser.ast.nodeTypes.NodeWithSimpleName<?> node) {
        return node.getName().getBegin().map(position -> position.line).orElse(null);
    }
}
