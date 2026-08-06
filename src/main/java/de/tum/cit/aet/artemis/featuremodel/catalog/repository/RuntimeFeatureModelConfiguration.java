package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import tools.jackson.databind.ObjectMapper;

/** Creates the process-stable runtime feature-model bundle during application startup. */
@Configuration
public class RuntimeFeatureModelConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RuntimeFeatureModelConfiguration.class);

    /**
     * Loads the complete configured bundle eagerly so an invalid snapshot stops startup before requests are served.
     *
     * @param properties explicit runtime source properties.
     * @param resourceLoader classpath resource loader.
     * @param objectMapper mapper shared by runtime stores.
     * @return validated process-stable runtime bundle.
     */
    @Bean
    public RuntimeFeatureModelBundle runtimeFeatureModelBundle(SnapshotProperties properties, ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        RuntimeFeatureModelBundle bundle = new RuntimeFeatureModelBundleLoader(properties, resourceLoader, objectMapper).load();
        RuntimeFeatureModelProvenance provenance = bundle.provenance();
        log.info("Activated {} feature model bundle '{}@{}'{}.", provenance.sourceMode().value(), provenance.modelId(), provenance.modelVersion(),
                provenance.snapshotId() == null ? "" : " from validated snapshot '" + provenance.snapshotId() + "'");
        return bundle;
    }
}
