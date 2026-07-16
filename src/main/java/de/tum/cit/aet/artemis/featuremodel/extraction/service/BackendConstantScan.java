package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;

/**
 * Scans the backend {@code Constants} class for string constants: module feature ids, enabled property names, and
 * Spring profile names. Constants with non-literal initializers (such as concatenated profile expressions) are
 * deliberately skipped because they do not declare a single anchor value.
 */
class BackendConstantScan {

    static final String DEFAULT_CONSTANTS_PATH = "src/main/java/de/tum/cit/aet/artemis/core/config/Constants.java";

    /**
     * One scanned string constant.
     *
     * @param name constant name.
     * @param value literal string value.
     * @param line 1-based declaration line.
     * @param javadoc javadoc description text, or null when the constant has none.
     */
    record ScannedConstant(String name, String value, Integer line, String javadoc) {
    }

    /**
     * Scan result of the constants class.
     *
     * @param file checkout-relative path of the scanned file.
     * @param constants scanned string constants in declaration order.
     */
    record Result(String file, List<ScannedConstant> constants) {

        /**
         * Creates an empty result for a failed or skipped scan.
         *
         * @return result without constants.
         */
        static Result empty() {
            return new Result(null, List.of());
        }
    }

    /**
     * Scans the constants class of the given checkout.
     *
     * @param source Artemis source repository.
     * @return scanned constants.
     * @throws IOException if the constants file cannot be read.
     * @throws IllegalArgumentException if the constants file cannot be located or parsed.
     */
    Result scan(ArtemisSourceRepository source) throws IOException {
        String file = locateConstantsFile(source);
        CompilationUnit unit = JavaSourceParser.parse(source.readFile(file), file);
        List<ScannedConstant> constants = new ArrayList<>();
        for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
            if (!field.isStatic() || !field.isFinal() || !"String".equals(field.getElementType().asString())) {
                continue;
            }
            String javadoc = field.getJavadoc().map(doc -> doc.getDescription().toText().trim()).orElse(null);
            field.getVariables().forEach(variable -> variable.getInitializer().ifPresent(initializer -> {
                if (initializer instanceof StringLiteralExpr literal) {
                    constants.add(new ScannedConstant(variable.getNameAsString(), literal.getValue(), JavaSourceParser.lineOf(variable), javadoc));
                }
            }));
        }
        return new Result(file, List.copyOf(constants));
    }

    /**
     * Locates the backend constants file, preferring the known location and falling back to a name-based search so a
     * moved file is still found by symbol.
     *
     * @param source Artemis source repository.
     * @return checkout-relative path of the constants file.
     * @throws IOException if the search fails.
     * @throws IllegalArgumentException if no constants file declaring module features can be found.
     */
    private String locateConstantsFile(ArtemisSourceRepository source) throws IOException {
        if (source.fileExists(DEFAULT_CONSTANTS_PATH)) {
            return DEFAULT_CONSTANTS_PATH;
        }
        for (String candidate : source.findFilesByName("src/main/java", "Constants.java")) {
            Optional<String> content = readIfPossible(source, candidate);
            if (content.isPresent() && content.get().contains("MODULE_FEATURE_")) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("No Constants.java declaring MODULE_FEATURE_ constants found under src/main/java.");
    }

    /**
     * Reads a file and swallows read failures so a broken sibling file cannot abort the location search.
     *
     * @param source Artemis source repository.
     * @param relativePath checkout-relative path.
     * @return file content, or empty when unreadable.
     */
    private Optional<String> readIfPossible(ArtemisSourceRepository source, String relativePath) {
        try {
            return Optional.of(source.readFile(relativePath));
        }
        catch (IOException e) {
            return Optional.empty();
        }
    }
}
