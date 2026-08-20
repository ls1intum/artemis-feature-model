package de.tum.cit.aet.artemis.featuremodel.extraction.pipeline;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.report.ExtractionReportAssembler;

/** Writes a minimal consolidated JSON and HTML verdict when a stage cannot consume its normal inputs. */
public class ControlledFailureReportWriter {

    private final ExtractionArtifactStore artifactStore;

    /**
     * Creates the writer.
     *
     * @param artifactStore artifact boundary that writes both report formats.
     */
    public ControlledFailureReportWriter(ExtractionArtifactStore artifactStore) {
        this.artifactStore = artifactStore;
    }

    /**
     * Writes a deterministic failure report for a controlled stage exception.
     *
     * @param context immutable run identity.
     * @param failure failure returned by the stage.
     * @throws IOException if the report cannot be written.
     */
    public void write(ExtractionRunContext context, Exception failure) throws IOException {
        CurationReport curation = new CurationReport(context.manifest().manifestVersion(), context.artemisCommit(),
                Map.of(CurationReport.STATE_INCLUDE, 0, CurationReport.STATE_EXCLUDE, 0, CurationReport.STATE_UNDECLARED, 0), Map.of(),
                List.of(), List.of());
        ReportItem item = ReportItem.error(ReportItem.CODE_PIPELINE_ARTIFACT_INVALID, "pipeline", failure.getMessage());
        ExtractionReport report = new ExtractionReportAssembler().assemble(context.artemisCommit(), context.manifestDigest(), curation, List.of(item), false);
        artifactStore.writeReport(context.layout(), report);
    }
}
