package de.tum.cit.aet.artemis.featuremodel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
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
