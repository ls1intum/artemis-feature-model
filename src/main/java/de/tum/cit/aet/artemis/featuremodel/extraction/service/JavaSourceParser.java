package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;

/**
 * Thin wrapper around JavaParser with a shared configuration for all backend anchor scans. JavaParser was chosen over
 * line-based parsing because the anchors need structural facts (javadoc, initializers, method bodies), and the next
 * phase requires annotation parsing anyway.
 */
final class JavaSourceParser {

    private static final ParserConfiguration CONFIGURATION = new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);

    private JavaSourceParser() {
    }

    /**
     * Parses Java source text into a compilation unit.
     *
     * @param source Java source text.
     * @param sourceLabel label used in the failure message, typically the checkout-relative path.
     * @return parsed compilation unit.
     * @throws IllegalArgumentException if the source cannot be parsed.
     */
    static CompilationUnit parse(String source, String sourceLabel) {
        ParseResult<CompilationUnit> result = new JavaParser(CONFIGURATION).parse(source);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            throw new IllegalArgumentException("Could not parse " + sourceLabel + ": " + result.getProblems());
        }
        return result.getResult().get();
    }

    /**
     * Returns the 1-based begin line of a node.
     *
     * @param node parsed node.
     * @return begin line, or null when the node carries no position information.
     */
    static Integer lineOf(Node node) {
        return node.getBegin().map(position -> position.line).orElse(null);
    }
}
