package de.tum.cit.aet.artemis.featuremodel.extraction.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedAnnotation;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedAnnotationSemantics.ConfigurationDeclaration;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.SourceScanResult;

/** Covers contract-v2 anchor extraction, nested configuration parsing, and retired-attribute rejection. */
class ArtemisFeatureAnnotationScanTest {

    private static final Path ANNOTATED_FIXTURE = Path.of("src/test/resources/extraction/annotated-artemis");

    private static final Path RETIRED_FIXTURE = Path.of("src/test/resources/extraction/annotated-artemis-retired");

    @Test
    void parsesTypeFieldAndEnumMemberAnchorsWithIdAndConfiguration() throws Exception {
        SourceScanResult<List<ExtractedAnnotation>> annotationScan = new ArtemisFeatureAnnotationScan()
                .scan(new LocalArtemisSourceRepository(ANNOTATED_FIXTURE));

        assertThat(annotationScan.diagnostics()).isEmpty();
        assertThat(annotationScan.facts()).hasSize(4);
        assertThat(annotationScan.facts()).anySatisfy(annotation -> {
            assertThat(annotation.anchor()).isEqualTo("de.tum.cit.aet.artemis.alpha.config.AlphaEnabled");
            assertThat(annotation.semantics().id()).isEqualTo("annotated-alpha");
            assertThat(annotation.semantics().configuration()).containsExactly(new ConfigurationDeclaration("artemis.alpha.url", false),
                    new ConfigurationDeclaration("artemis.alpha.secret", true));
        });
        assertThat(annotationScan.facts()).anySatisfy(annotation -> {
            assertThat(annotation.anchor()).isEqualTo("MODULE_FEATURE_FIELD_ALPHA");
            assertThat(annotation.semantics().configuration()).as("a single declaration is accepted without array braces")
                    .containsExactly(new ConfigurationDeclaration("artemis.field-alpha.url", false));
        });
        assertThat(annotationScan.facts()).anySatisfy(annotation -> {
            assertThat(annotation.anchor()).isEqualTo("toggle:ToggleField");
            assertThat(annotation.semantics().configuration()).isEmpty();
        });
    }

    @Test
    void rejectsARetiredContractV1AttributeWithAMigrationMessage() throws Exception {
        SourceScanResult<List<ExtractedAnnotation>> annotationScan = new ArtemisFeatureAnnotationScan()
                .scan(new LocalArtemisSourceRepository(RETIRED_FIXTURE));

        assertThat(annotationScan.facts()).isEmpty();
        assertThat(annotationScan.diagnostics()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_EXTRACTOR_ERROR);
            assertThat(item.message()).contains("retired attribute", "[group]", "'features'");
        });
    }
}
