package de.tum.cit.aet.artemis.featuremodel.extraction.scan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.JavaSourceParser;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.SourceScanResult;

/**
 * Scans all {@code *Enabled} Spring condition classes of the checkout. Classes are identified by symbol: any class
 * named {@code *Enabled} implementing {@code Condition}, regardless of package, because Artemis keeps most of them in
 * per-module {@code config} packages but not all of them. A single unparseable file is reported and skipped.
 */
class ConditionClassScan {

    /**
     * One scanned condition class.
     *
     * @param className simple class name, for example {@code IrisEnabled}.
     * @param file checkout-relative path of the class file.
     * @param line 1-based declaration line of the class.
     * @param packageName declared package name.
     * @param javadoc javadoc description of the class, or null.
     * @param accessorNames called methods starting with {@code is} inside {@code matches}, in source order.
     * @param propertyConstantNames referenced central property constant names inside {@code matches}.
     * @param literalPropertyKeys property keys read directly via string literals or local constants.
     */
    record ScannedCondition(String className, String file, Integer line, String packageName, String javadoc, List<String> accessorNames,
            List<String> propertyConstantNames, List<String> literalPropertyKeys) {
    }

    /**
     * Scan result over all condition classes.
     *
     * @param conditions scanned condition classes sorted by class name.
     */
    record Result(List<ScannedCondition> conditions) {

        /**
         * Creates an empty result for a failed or skipped scan.
         *
         * @return result without conditions.
         */
        static Result empty() {
            return new Result(List.of());
        }
    }

    /**
     * Scans all condition classes of the given checkout.
     *
     * @param source Artemis source repository.
     * @return scanned condition classes and per-file parse errors.
     * @throws IOException if the source tree cannot be traversed.
     */
    SourceScanResult<Result> scan(ArtemisSourceRepository source) throws IOException {
        List<ScannedCondition> conditions = new ArrayList<>();
        List<ReportItem> errors = new ArrayList<>();
        for (String file : source.findFiles(ArtemisSourceConventions.Roots.JAVA, ArtemisSourceConventions.Naming.CONDITION_FILE_SUFFIX)) {
            try {
                scanFile(source, file).ifPresent(conditions::add);
            }
            catch (IOException | RuntimeException e) {
                errors.add(ReportItem.error(ReportItem.CODE_EXTRACTOR_ERROR, file, "Could not parse condition class candidate: " + e.getMessage()));
            }
        }
        conditions.sort((first, second) -> first.className().compareTo(second.className()));
        return SourceScanResult.withDiagnostics(new Result(List.copyOf(conditions)), errors);
    }

    /**
     * Scans one file for a condition class declaration.
     *
     * @param source Artemis source repository.
     * @param file checkout-relative path.
     * @return scanned condition, or empty when the file does not declare a {@code Condition} implementation.
     * @throws IOException if the file cannot be read.
     * @throws IllegalArgumentException if the file cannot be parsed.
     */
    private Optional<ScannedCondition> scanFile(ArtemisSourceRepository source, String file) throws IOException {
        CompilationUnit unit = JavaSourceParser.parse(source.readFile(file), file);
        String packageName = unit.getPackageDeclaration().map(declaration -> declaration.getNameAsString()).orElse("");
        for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            boolean implementsCondition = type.getImplementedTypes().stream()
                    .anyMatch(implemented -> ArtemisSourceConventions.Symbols.CONDITION_INTERFACE.equals(implemented.getNameAsString()));
            if (!implementsCondition) {
                continue;
            }
            return Optional.of(scanConditionType(type, file, packageName));
        }
        return Optional.empty();
    }

    /**
     * Scans a parsed condition class declaration.
     *
     * @param type condition class declaration.
     * @param file checkout-relative path.
     * @param packageName declared package name.
     * @return scanned condition.
     */
    private ScannedCondition scanConditionType(ClassOrInterfaceDeclaration type, String file, String packageName) {
        Map<String, String> localConstants = scanLocalStringConstants(type);
        Set<String> accessorNames = new LinkedHashSet<>();
        Set<String> propertyConstantNames = new LinkedHashSet<>();
        Set<String> literalPropertyKeys = new LinkedHashSet<>();
        type.getMethodsByName("matches").stream().findFirst().flatMap(MethodDeclaration::getBody).ifPresent(body -> {
            body.findAll(MethodCallExpr.class).forEach(call -> {
                String callName = call.getNameAsString();
                if (callName.startsWith("is")) {
                    accessorNames.add(callName);
                }
                if ("getProperty".equals(callName) && !call.getArguments().isEmpty()) {
                    resolvePropertyArgument(call, localConstants, literalPropertyKeys, propertyConstantNames);
                }
            });
            body.findAll(NameExpr.class).stream().map(NameExpr::getNameAsString)
                    .filter(name -> name.endsWith(ArtemisSourceConventions.Naming.PROPERTY_CONSTANT_SUFFIX) && !localConstants.containsKey(name))
                    .forEach(propertyConstantNames::add);
            body.findAll(FieldAccessExpr.class).stream().map(FieldAccessExpr::getNameAsString)
                    .filter(name -> name.endsWith(ArtemisSourceConventions.Naming.PROPERTY_CONSTANT_SUFFIX))
                    .forEach(propertyConstantNames::add);
        });
        String javadoc = type.getJavadoc().map(doc -> doc.getDescription().toText().trim()).orElse(null);
        return new ScannedCondition(type.getNameAsString(), file, JavaSourceParser.lineOf(type), packageName, javadoc, List.copyOf(accessorNames),
                List.copyOf(propertyConstantNames), List.copyOf(literalPropertyKeys));
    }

    /**
     * Resolves the property key argument of an {@code Environment.getProperty} call.
     *
     * @param call the {@code getProperty} call.
     * @param localConstants local string constants of the class.
     * @param literalPropertyKeys sink for directly resolvable property keys.
     * @param propertyConstantNames sink for central property constant references.
     */
    private void resolvePropertyArgument(MethodCallExpr call, Map<String, String> localConstants, Set<String> literalPropertyKeys, Set<String> propertyConstantNames) {
        var argument = call.getArgument(0);
        if (argument instanceof StringLiteralExpr literal) {
            literalPropertyKeys.add(literal.getValue());
        }
        else if (argument instanceof NameExpr name && localConstants.containsKey(name.getNameAsString())) {
            literalPropertyKeys.add(localConstants.get(name.getNameAsString()));
        }
        else if (argument instanceof NameExpr name && name.getNameAsString().endsWith(ArtemisSourceConventions.Naming.PROPERTY_CONSTANT_SUFFIX)) {
            propertyConstantNames.add(name.getNameAsString());
        }
        else if (argument instanceof FieldAccessExpr fieldAccess
                && fieldAccess.getNameAsString().endsWith(ArtemisSourceConventions.Naming.PROPERTY_CONSTANT_SUFFIX)) {
            propertyConstantNames.add(fieldAccess.getNameAsString());
        }
    }

    /**
     * Collects local {@code String} constants of a condition class, used to resolve indirect property key reads.
     *
     * @param type condition class declaration.
     * @return map from constant name to literal value.
     */
    private Map<String, String> scanLocalStringConstants(ClassOrInterfaceDeclaration type) {
        Map<String, String> constants = new LinkedHashMap<>();
        type.getFields().forEach(field -> field.getVariables().forEach(variable -> variable.getInitializer().ifPresent(initializer -> {
            if (initializer instanceof StringLiteralExpr literal) {
                constants.put(variable.getNameAsString(), literal.getValue());
            }
        })));
        return constants;
    }
}
