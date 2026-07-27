package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;

import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;

/**
 * Scans {@code ArtemisConfigHelper}: the per-module {@code isXEnabled} accessors with the property constants they
 * read, and the {@code getEnabledFeatures} enumeration that is the authoritative runtime module list.
 */
class ConfigHelperScan {

    static final String DEFAULT_CONFIG_HELPER_PATH = "src/main/java/de/tum/cit/aet/artemis/core/config/ArtemisConfigHelper.java";

    private static final String PROPERTY_CONSTANT_SUFFIX = "_PROPERTY_NAME";

    private static final String ENUMERATION_METHOD_NAME = "getEnabledFeatures";

    /**
     * One scanned accessor method.
     *
     * @param name method name, for example {@code isIrisEnabled}.
     * @param line 1-based declaration line.
     * @param propertyConstantNames referenced property constant names in source order.
     * @param nestedAccessorNames called methods starting with {@code is}, resolved transitively later.
     */
    record ScannedAccessor(String name, Integer line, List<String> propertyConstantNames, List<String> nestedAccessorNames) {
    }

    /**
     * One constant reference inside the {@code getEnabledFeatures} enumeration.
     *
     * @param constantName referenced constant name.
     * @param line 1-based line of the reference.
     */
    record EnumerationEntry(String constantName, Integer line) {
    }

    /**
     * Scan result of the config helper class.
     *
     * @param file checkout-relative path of the scanned file.
     * @param accessors scanned accessor methods in declaration order.
     * @param enumerationEntries constant references of the runtime enumeration in source order.
     */
    record Result(String file, List<ScannedAccessor> accessors, List<EnumerationEntry> enumerationEntries) {

        /**
         * Creates an empty result for a failed or skipped scan.
         *
         * @return result without accessors.
         */
        static Result empty() {
            return new Result(null, List.of(), List.of());
        }
    }

    /**
     * Scans the config helper of the given checkout.
     *
     * @param source Artemis source repository.
     * @return scanned accessors and enumeration entries.
     * @throws IOException if the config helper file cannot be read.
     * @throws IllegalArgumentException if the config helper cannot be located or parsed.
     */
    Result scan(ArtemisSourceRepository source) throws IOException {
        String file = locateConfigHelperFile(source);
        CompilationUnit unit = JavaSourceParser.parse(source.readFile(file), file);
        List<ScannedAccessor> accessors = new ArrayList<>();
        List<EnumerationEntry> enumerationEntries = new ArrayList<>();
        for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
            if (ENUMERATION_METHOD_NAME.equals(method.getNameAsString())) {
                enumerationEntries.addAll(scanEnumeration(method));
            }
            else if (method.getNameAsString().startsWith("is")) {
                accessors.add(scanAccessor(method));
            }
        }
        return new Result(file, List.copyOf(accessors), List.copyOf(enumerationEntries));
    }

    /**
     * Scans one accessor method for property constant references and nested accessor calls.
     *
     * @param method accessor method.
     * @return scanned accessor.
     */
    private ScannedAccessor scanAccessor(MethodDeclaration method) {
        Set<String> propertyConstants = new LinkedHashSet<>();
        Set<String> nestedAccessors = new LinkedHashSet<>();
        method.getBody().ifPresent(body -> {
            body.findAll(NameExpr.class).stream().map(NameExpr::getNameAsString).filter(name -> name.endsWith(PROPERTY_CONSTANT_SUFFIX)).forEach(propertyConstants::add);
            body.findAll(FieldAccessExpr.class).stream().map(FieldAccessExpr::getNameAsString).filter(name -> name.endsWith(PROPERTY_CONSTANT_SUFFIX))
                    .forEach(propertyConstants::add);
            body.findAll(MethodCallExpr.class).stream().map(MethodCallExpr::getNameAsString).filter(name -> name.startsWith("is") && !name.equals(method.getNameAsString()))
                    .forEach(nestedAccessors::add);
        });
        return new ScannedAccessor(method.getNameAsString(), JavaSourceParser.lineOf(method), List.copyOf(propertyConstants), List.copyOf(nestedAccessors));
    }

    /**
     * Scans the runtime enumeration method for constant references passed to {@code add} calls.
     *
     * @param method enumeration method.
     * @return enumeration entries in source order.
     */
    private List<EnumerationEntry> scanEnumeration(MethodDeclaration method) {
        List<EnumerationEntry> entries = new ArrayList<>();
        method.getBody().ifPresent(body -> body.findAll(MethodCallExpr.class).stream().filter(call -> "add".equals(call.getNameAsString())).forEach(call -> {
            if (call.getArguments().size() != 1) {
                return;
            }
            Expression argument = call.getArgument(0);
            if (argument instanceof FieldAccessExpr fieldAccess) {
                entries.add(new EnumerationEntry(fieldAccess.getNameAsString(), JavaSourceParser.lineOf(fieldAccess)));
            }
            else if (argument instanceof NameExpr name) {
                entries.add(new EnumerationEntry(name.getNameAsString(), JavaSourceParser.lineOf(name)));
            }
        }));
        return entries;
    }

    /**
     * Locates the config helper file, preferring the known location and falling back to a name-based search.
     *
     * @param source Artemis source repository.
     * @return checkout-relative path of the config helper.
     * @throws IOException if the search fails.
     * @throws IllegalArgumentException if no config helper file can be found.
     */
    private String locateConfigHelperFile(ArtemisSourceRepository source) throws IOException {
        if (source.fileExists(DEFAULT_CONFIG_HELPER_PATH)) {
            return DEFAULT_CONFIG_HELPER_PATH;
        }
        List<String> matches = source.findFilesByName("src/main/java", "ArtemisConfigHelper.java");
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No ArtemisConfigHelper.java found under src/main/java.");
        }
        return matches.getFirst();
    }
}
