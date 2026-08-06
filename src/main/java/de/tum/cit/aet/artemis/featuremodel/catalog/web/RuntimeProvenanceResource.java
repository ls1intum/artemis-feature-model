package de.tum.cit.aet.artemis.featuremodel.catalog.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.featuremodel.catalog.dto.RuntimeProvenanceDTO;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelBundle;

/** Exposes safe, read-only identity for deployment health and provenance verification. */
@RestController
@RequestMapping("/api/feature-model/provenance")
public class RuntimeProvenanceResource {

    private final RuntimeFeatureModelBundle runtimeBundle;

    /**
     * Creates the read-only provenance resource.
     *
     * @param runtimeBundle validated process-stable runtime bundle.
     */
    public RuntimeProvenanceResource(RuntimeFeatureModelBundle runtimeBundle) {
        this.runtimeBundle = runtimeBundle;
    }

    /**
     * Returns safe identity fields for the active runtime bundle.
     *
     * @return active source, model, and optional snapshot identity.
     */
    @GetMapping
    public RuntimeProvenanceDTO getActiveProvenance() {
        return RuntimeProvenanceDTO.from(runtimeBundle.provenance());
    }
}
