package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.nio.file.Path;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionStage;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
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
     * @param validationStatus automation status of the guided workflow validation.
     * @param findingCount number of guided workflow findings.
     * @param workflowIntegrityValid whether the workflow passed hard reference validation against the model.
     */
    public record Summary(Path workflowDirectory, String validationStatus, int findingCount, boolean workflowIntegrityValid) {
    }

    /**
     * Runs one workflow preparation.
     *
     * @param inputs resolved command inputs.
     * @return summary of the written workflow artifacts.
     * @throws IOException if an input cannot be read or an artifact cannot be written.
     */
    public Summary run(FeatureExtractionInputs inputs) throws IOException {
        ExtractionRunContext context = inputLoader.runContext(inputs);
        artifactStore.invalidateFrom(context.layout(), ExtractionStage.WORKFLOW);
        try {
            ExtractionArtifactStore.LoadedScan scan = artifactStore.readScan(context.layout(), context.artemisCommit());
            ExtractionArtifactStore.LoadedModel model = artifactStore.readModel(context.layout(), context.artemisCommit(), scan.result().payloadDigest(),
                    context.manifestDigest());

            byte[] authoredWorkflowBytes = inputLoader.authoredWorkflowBytes(inputs);
            GuidedWorkflow authoredWorkflow = objectMapper.readValue(authoredWorkflowBytes, GuidedWorkflow.class);
            GuidedWorkflowValidator.Result validation = new GuidedWorkflowValidator().validate(model.generatedModel(), authoredWorkflow,
                    inputLoader.deploymentProfile(inputs));
            artifactStore.writeWorkflow(context.layout(), validation, authoredWorkflowBytes, model.result().generatedModelDigest(), context.artemisCommit());

            return new Summary(context.layout().workflowDirectory(), validation.guidedValidation().status(), validation.guidedValidation().findings().size(),
                    validation.workflowIntegrityValid());
        }
        catch (IOException | RuntimeException failure) {
            new ControlledFailureReportWriter(artifactStore).write(context, failure);
            throw failure;
        }
    }
}
