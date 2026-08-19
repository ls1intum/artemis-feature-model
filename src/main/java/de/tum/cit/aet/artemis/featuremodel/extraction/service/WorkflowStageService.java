package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionStage;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.pipeline.ControlledFailureReportWriter;
import de.tum.cit.aet.artemis.featuremodel.extraction.pipeline.ExtractionArtifactStore;
import de.tum.cit.aet.artemis.featuremodel.extraction.pipeline.ExtractionInputLoader;
import de.tum.cit.aet.artemis.featuremodel.extraction.pipeline.ExtractionRunContext;
import de.tum.cit.aet.artemis.featuremodel.extraction.pipeline.WorkflowValidationOutcome;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import tools.jackson.databind.ObjectMapper;

/**
 * The {@code prepareGuidedWorkflow} command: validates the authored lean guided workflow against the generated model
 * and prepares the build copy the snapshot embeds. It reads the authored workflow but never writes to it — changing
 * the authored file stays the deliberate job of {@code syncGuidedWorkflowScaffold}.
 */
public class WorkflowStageService {

    private final ExtractionInputLoader inputLoader;

    private final ExtractionArtifactStore artifactStore;

    private final ObjectMapper objectMapper;

    /**
     * Creates the workflow preparation command.
     *
     * @param objectMapper Jackson mapper shared with the validator and the artifact store.
     */
    public WorkflowStageService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.inputLoader = new ExtractionInputLoader(objectMapper);
        this.artifactStore = new ExtractionArtifactStore(objectMapper);
    }

    /**
     * Result of one workflow preparation command.
     *
     * @param workflowDirectory directory the workflow artifacts were written to.
     * @param validationStatus status of the guided workflow validation, {@code pass} or {@code findings}.
     * @param workflowIntegrityValid whether the effective workflow passed hard reference validation against the model.
     * @param deliveryEligible whether hard references passed and no error-severity finding exists.
     * @param severityCounts guided workflow finding counts per severity, sorted by severity.
     * @param codeCounts guided workflow finding counts per code, sorted by code.
     */
    public record Summary(Path workflowDirectory, String validationStatus, boolean workflowIntegrityValid, boolean deliveryEligible,
            Map<String, Integer> severityCounts, Map<String, Integer> codeCounts) {
    }

    /**
     * Runs one workflow preparation.
     *
     * @param inputs resolved command inputs.
     * @param sourceFactory creates the source repository over the configured checkout for revision derivation.
     * @return summary of the written workflow artifacts.
     * @throws IOException if an input cannot be read or an artifact cannot be written.
     */
    public Summary run(FeatureExtractionInputs inputs, Function<Path, ArtemisSourceRepository> sourceFactory) throws IOException {
        ArtemisSourceRepository source = inputLoader.verifiedSource(inputs, sourceFactory);
        ExtractionRunContext context = inputLoader.runContext(inputs, source);
        artifactStore.invalidateFrom(context.layout(), ExtractionStage.WORKFLOW);
        try {
            ExtractionArtifactStore.LoadedScan scan = artifactStore.readScan(context.layout(), context.artemisCommit());
            ExtractionArtifactStore.LoadedModel model = artifactStore.readModel(context.layout(), context.artemisCommit(), scan.result().payloadDigest(),
                    context.manifestDigest());

            byte[] authoredWorkflowBytes = inputLoader.authoredWorkflowBytes(inputs);
            GuidedWorkflow authoredWorkflow = objectMapper.readValue(authoredWorkflowBytes, GuidedWorkflow.class);
            WorkflowValidationOutcome validation = new GuidedWorkflowValidator().validate(model.generatedModel(), authoredWorkflow,
                    inputLoader.deploymentProfile(inputs));
            artifactStore.writeWorkflow(context.layout(), validation, authoredWorkflowBytes, model.result().generatedModelDigest(), context.artemisCommit());

            return new Summary(context.layout().workflowDirectory(), validation.guidedValidation().status(), validation.workflowIntegrityValid(),
                    validation.deliveryEligible(), validation.guidedValidation().severityCounts(), validation.guidedValidation().codeCounts());
        }
        catch (IOException | RuntimeException failure) {
            new ControlledFailureReportWriter(artifactStore).write(context, failure);
            throw failure;
        }
    }
}
