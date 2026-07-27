package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;

import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;

/**
 * Scans the backend {@code Feature} enum that declares the runtime feature toggles. The enum is located by symbol: a
 * {@code Feature.java} file declaring {@code enum Feature}, preferring the known location under
 * {@code core/service/feature}.
 */
class BackendFeatureEnumScan {

    static final String DEFAULT_FEATURE_ENUM_PATH = "src/main/java/de/tum/cit/aet/artemis/core/service/feature/Feature.java";

    private static final String ENUM_NAME = "Feature";

    /**
     * One scanned enum member.
     *
     * @param name enum member name.
     * @param line 1-based declaration line.
     */
    record ScannedEnumMember(String name, Integer line) {
    }

    /**
     * Scan result of the backend feature enum.
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
     * Scans the backend feature enum of the given checkout.
     *
     * @param source Artemis source repository.
     * @return scanned enum members.
     * @throws IOException if the enum file cannot be read.
     * @throws IllegalArgumentException if no feature enum can be located or parsed.
     */
    Result scan(ArtemisSourceRepository source) throws IOException {
        String file = locateFeatureEnumFile(source);
        CompilationUnit unit = JavaSourceParser.parse(source.readFile(file), file);
        EnumDeclaration enumDeclaration = unit.findAll(EnumDeclaration.class).stream().filter(declaration -> ENUM_NAME.equals(declaration.getNameAsString())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("File " + file + " does not declare enum " + ENUM_NAME + "."));
        List<ScannedEnumMember> members = new ArrayList<>();
        for (EnumConstantDeclaration constant : enumDeclaration.getEntries()) {
            members.add(new ScannedEnumMember(constant.getNameAsString(), JavaSourceParser.lineOf(constant)));
        }
        return new Result(file, List.copyOf(members));
    }

    /**
     * Locates the feature enum file, preferring the known location and falling back to a content-checked name search.
     *
     * @param source Artemis source repository.
     * @return checkout-relative path of the feature enum.
     * @throws IOException if the search fails.
     * @throws IllegalArgumentException if no feature enum file can be found.
     */
    private String locateFeatureEnumFile(ArtemisSourceRepository source) throws IOException {
        if (source.fileExists(DEFAULT_FEATURE_ENUM_PATH)) {
            return DEFAULT_FEATURE_ENUM_PATH;
        }
        for (String candidate : source.findFilesByName("src/main/java", "Feature.java")) {
            Optional<String> content = readIfPossible(source, candidate);
            if (content.isPresent() && content.get().contains("enum " + ENUM_NAME)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("No Feature.java declaring enum Feature found under src/main/java.");
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
