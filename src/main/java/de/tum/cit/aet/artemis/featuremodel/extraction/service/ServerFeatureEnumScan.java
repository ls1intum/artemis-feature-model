package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;

import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceLocator;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.JavaSourceParser;

/**
 * Scans the server {@code Feature} enum that declares the runtime feature toggles. The enum is located by symbol: a
 * {@code Feature.java} file declaring {@code enum Feature}, preferring the known location under
 * {@code core/service/feature}.
 */
class ServerFeatureEnumScan {

    private final ArtemisSourceLocator sourceLocator = new ArtemisSourceLocator();

    /**
     * One scanned enum member.
     *
     * @param name enum member name.
     * @param line 1-based declaration line.
     */
    record ScannedEnumMember(String name, Integer line) {
    }

    /**
     * Scan result of the server feature enum.
     *
     * @param file checkout-relative path of the enum file.
     * @param members enum members in declaration order.
     */
    record Result(String file, List<ScannedEnumMember> members) {

        /**
         * Creates an empty result for a failed or skipped scan.
         *
         * @return result without members.
         */
        static Result empty() {
            return new Result(null, List.of());
        }
    }

    /**
     * Scans the server feature enum of the given checkout.
     *
     * @param source Artemis source repository.
     * @return scanned enum members.
     * @throws IOException if the enum file cannot be read.
     * @throws IllegalArgumentException if no feature enum can be located or parsed.
     */
    Result scan(ArtemisSourceRepository source) throws IOException {
        String file = sourceLocator.locate(source, ArtemisSourceConventions.Files.SERVER_FEATURE_ENUM,
                "enum " + ArtemisSourceConventions.Symbols.SERVER_FEATURE_ENUM,
                content -> content.contains("enum " + ArtemisSourceConventions.Symbols.SERVER_FEATURE_ENUM));
        CompilationUnit unit = JavaSourceParser.parse(source.readFile(file), file);
        EnumDeclaration enumDeclaration = unit.findAll(EnumDeclaration.class).stream()
                .filter(declaration -> ArtemisSourceConventions.Symbols.SERVER_FEATURE_ENUM.equals(declaration.getNameAsString())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "File " + file + " does not declare enum " + ArtemisSourceConventions.Symbols.SERVER_FEATURE_ENUM + "."));
        List<ScannedEnumMember> members = new ArrayList<>();
        for (EnumConstantDeclaration constant : enumDeclaration.getEntries()) {
            members.add(new ScannedEnumMember(constant.getNameAsString(), JavaSourceParser.lineOf(constant)));
        }
        return new Result(file, List.copyOf(members));
    }

}
