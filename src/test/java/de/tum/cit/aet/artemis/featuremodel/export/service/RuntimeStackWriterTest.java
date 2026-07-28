package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.export.domain.TechnicalSelection;

class RuntimeStackWriterTest {

    private final RuntimeStackWriter writer = new RuntimeStackWriter();

    @Test
    void postgresIclStackIsByteIdenticalToTheRecordedFixture() throws IOException {
        TechnicalSelection selection = new TechnicalSelection(List.of("localci", "buildagent", "localvc"),
                Optional.of("docker/postgres.yml"), Optional.of("postgresql"), Optional.of("integrated-code-lifecycle"));

        String stack = writer.write(selection);

        assertThat(stack).isEqualTo(fixture("/fixtures/postgres-icl-technical-stack.yml"));
    }

    private String fixture(String path) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(path)) {
            assertThat(inputStream).as("fixture resource %s", path).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
