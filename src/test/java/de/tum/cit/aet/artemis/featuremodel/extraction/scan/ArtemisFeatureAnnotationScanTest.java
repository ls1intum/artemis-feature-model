package de.tum.cit.aet.artemis.featuremodel.extraction.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedAnnotation;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.SourceScanResult;

/** Covers annotation anchor extraction and attribute parsing on the annotated fixture. */
class ArtemisFeatureAnnotationScanTest {

    private static final Path ANNOTATED_FIXTURE = Path.of("src/test/resources/extraction/annotated-artemis");

    @Test
    void parsesTypeFieldAndEnumMemberAnchorsWithTheirSemantics() throws Exception {
        SourceScanResult<List<ExtractedAnnotation>> annotationScan = new ArtemisFeatureAnnotationScan()
                .scan(new LocalArtemisSourceRepository(ANNOTATED_FIXTURE));

        assertThat(annotationScan.diagnostics()).isEmpty();
        assertThat(annotationScan.facts()).hasSize(4);
        assertThat(annotationScan.facts()).anySatisfy(annotation -> {
            assertThat(annotation.anchor()).isEqualTo("de.tum.cit.aet.artemis.alpha.config.AlphaEnabled");
            assertThat(annotation.semantics().id()).isEqualTo("annotated-alpha");
            assertThat(annotation.semantics().requiresCapabilities()).containsExactly("annotation-service", "annotation-secret");
        });
        assertThat(annotationScan.facts()).extracting(ExtractedAnnotation::anchor).contains("MODULE_FEATURE_FIELD_ALPHA", "toggle:ToggleField");
    }
}
