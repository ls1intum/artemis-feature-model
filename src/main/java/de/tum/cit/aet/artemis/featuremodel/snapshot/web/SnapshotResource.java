package de.tum.cit.aet.artemis.featuremodel.snapshot.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.featuremodel.snapshot.dto.ImportSnapshotRequest;
import de.tum.cit.aet.artemis.featuremodel.snapshot.dto.ImportSnapshotResultDTO;
import de.tum.cit.aet.artemis.featuremodel.snapshot.dto.SnapshotDetailDTO;
import de.tum.cit.aet.artemis.featuremodel.snapshot.dto.SnapshotSummaryDTO;
import de.tum.cit.aet.artemis.featuremodel.snapshot.service.SnapshotService;

@RestController
@RequestMapping("/api/feature-model/snapshots")
@ConditionalOnProperty(prefix = "artemis.feature-model", name = "snapshot-admin-api-enabled", havingValue = "true")
public class SnapshotResource {

    private static final Logger log = LoggerFactory.getLogger(SnapshotResource.class);

    private static final MediaType ZIP_MEDIA_TYPE = MediaType.parseMediaType("application/zip");

    private final SnapshotService snapshotService;

    /**
     * Creates the snapshot resource.
     *
     * @param snapshotService service used to list, import, and export local snapshots.
     */
    public SnapshotResource(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    /**
     * Lists imported local snapshots and their metadata.
     *
     * @return imported snapshot summaries.
     */
    @GetMapping
    public List<SnapshotSummaryDTO> listSnapshots() {
        log.debug("REST request to list imported feature model snapshots.");
        List<SnapshotSummaryDTO> snapshots = snapshotService.listSnapshots();
        log.info("REST response lists {} imported feature model snapshots.", snapshots.size());
        return snapshots;
    }

    /**
     * Returns the detail of an imported snapshot.
     *
     * @param snapshotId snapshot id.
     * @return snapshot detail.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.SnapshotException if the id is invalid or unknown.
     */
    @GetMapping("/{snapshotId}")
    public SnapshotDetailDTO getSnapshot(@PathVariable String snapshotId) {
        log.debug("REST request to get imported feature model snapshot '{}'.", snapshotId);
        return snapshotService.getSnapshot(snapshotId);
    }

    /**
     * Imports a local snapshot folder into the application data root.
     *
     * @param request import request with the source path and target id.
     * @return import result with the registered snapshot detail.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.SnapshotException if the source is missing or validation fails.
     */
    @PostMapping("/import")
    public ResponseEntity<ImportSnapshotResultDTO> importSnapshot(@RequestBody ImportSnapshotRequest request) {
        log.debug("REST request to import a feature model snapshot from '{}'.", request.sourcePath());
        ImportSnapshotResultDTO result = snapshotService.importSnapshot(request);
        log.info("REST response imported feature model snapshot '{}'.", result.snapshotId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * Exports an imported snapshot as a zip archive.
     *
     * @param snapshotId snapshot id.
     * @return zip archive download.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.SnapshotException if the id is invalid or unknown.
     */
    @GetMapping("/{snapshotId}/export")
    public ResponseEntity<Resource> exportSnapshot(@PathVariable String snapshotId) {
        log.debug("REST request to export feature model snapshot '{}'.", snapshotId);
        byte[] archive = snapshotService.exportSnapshot(snapshotId);
        log.info("REST response exported feature model snapshot '{}' as a {}-byte archive.", snapshotId, archive.length);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + snapshotId + ".zip\"").contentType(ZIP_MEDIA_TYPE)
                .contentLength(archive.length).body(new ByteArrayResource(archive));
    }
}
