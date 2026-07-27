package de.tum.cit.aet.artemis.featuremodel.extraction;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.TestFeatureModels;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GeneratedArtifactValidation;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.FeatureExtractionService;

/** Covers the command status policy for hard generated-artifact validation. */
class FeatureExtractionRunnerTest {

    @Test
    void hardValidationFailureMakesRunnerFailAfterDiagnostics() {
        FeatureExtractionService.Outcome outcome = outcome(new GeneratedArtifactValidation(false, true));

        assertThatThrownBy(() -> FeatureExtractionRunner.failIfSnapshotIneligible(outcome)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Diagnostics were written").hasMessageContaining("no importable snapshot was published");
    }

    @Test
    void validGeneratedArtifactsKeepSuccessfulStatus() {
        FeatureExtractionService.Outcome outcome = outcome(new GeneratedArtifactValidation(true, true));

        assertThatCode(() -> FeatureExtractionRunner.failIfSnapshotIneligible(outcome)).doesNotThrowAnyException();
    }

    private FeatureExtractionService.Outcome outcome(GeneratedArtifactValidation validation) {
        return new FeatureExtractionService.Outcome(List.of(), List.of(), List.of(), null, List.of(), TestFeatureModels.baseModel(), null, null, null,
                validation);
    }
}
