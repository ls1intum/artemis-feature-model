package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class EnvExampleWriterTest {

    private final EnvExampleWriter writer = new EnvExampleWriter();

    @Test
    void writesSortedDeduplicatedVariablesWithEmptyValues() {
        String env = writer.write(List.of("SPRING_AI_OPENAI_API_KEY", "ARTEMIS_IRIS_SECRET_TOKEN", "ARTEMIS_ATHENA_SECRET", "ARTEMIS_IRIS_SECRET_TOKEN"));

        assertThat(env).isEqualTo("ARTEMIS_ATHENA_SECRET=\nARTEMIS_IRIS_SECRET_TOKEN=\nSPRING_AI_OPENAI_API_KEY=\n");
    }

    @Test
    void doesNotEmitValuesForVariables() {
        String env = writer.write(List.of("ARTEMIS_IRIS_SECRET_TOKEN"));

        assertThat(env.strip()).isEqualTo("ARTEMIS_IRIS_SECRET_TOKEN=");
    }

    @Test
    void returnsEmptyStringForNoVariables() {
        assertThat(writer.write(List.of())).isEmpty();
    }
}
