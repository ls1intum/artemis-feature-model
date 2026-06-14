package de.tum.cit.aet.artemis.featuremodel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FeatureModelApp {

    /**
     * Starts the standalone feature model Spring Boot application.
     *
     * @param args command-line arguments passed to Spring Boot.
     */
    public static void main(String[] args) {
        SpringApplication.run(FeatureModelApp.class, args);
    }
}
