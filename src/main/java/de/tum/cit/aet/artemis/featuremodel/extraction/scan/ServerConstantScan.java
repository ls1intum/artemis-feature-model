package de.tum.cit.aet.artemis.featuremodel.extraction.scan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceLocator;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.JavaSourceParser;

/**
 * Scans the server {@code Constants} class for string constants: module feature ids, enabled property names, and
 * Spring profile names. Constants with non-literal initializers (such as concatenated profile expressions) are
 * deliberately skipped because they do not declare a single anchor value.
 */
class ServerConstantScan {

    private final ArtemisSourceLocator sourceLocator = new ArtemisSourceLocator();

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
        String file = sourceLocator.locate(source, ArtemisSourceConventions.Files.SERVER_CONSTANTS,
                "symbol prefix " + ArtemisSourceConventions.Symbols.MODULE_FEATURE_PREFIX,
                content -> content.contains(ArtemisSourceConventions.Symbols.MODULE_FEATURE_PREFIX));
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

}
