package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import de.tum.cit.aet.artemis.featuremodel.export.domain.OverlayEntry;

class YamlOverlayWriterTest {

    private final YamlOverlayWriter writer = new YamlOverlayWriter();

    @Test
    void expandsDottedPathsIntoNestedYamlWithTypedScalars() {
        List<OverlayEntry> entries = List.of(new OverlayEntry("artemis.iris.enabled", Boolean.TRUE), new OverlayEntry("artemis.iris.url", "https://pyris.example.com"),
                new OverlayEntry("artemis.iris.secret-token", "${ARTEMIS_IRIS_SECRET_TOKEN}"), new OverlayEntry("artemis.lecture.enabled", Boolean.FALSE),
                new OverlayEntry("artemis.iris.ratelimit.default-limit", -1L), new OverlayEntry("artemis.atlas.temperature", 0.8d),
                new OverlayEntry("spring.ai.openai.timeout", "5m"));

        String yaml = writer.write(entries);

        Map<String, Object> parsed = new Yaml().load(yaml);
        @SuppressWarnings("unchecked")
        Map<String, Object> artemis = (Map<String, Object>) parsed.get("artemis");
        @SuppressWarnings("unchecked")
        Map<String, Object> iris = (Map<String, Object>) artemis.get("iris");
        assertThat(iris.get("enabled")).isEqualTo(true);
        assertThat(iris.get("url")).isEqualTo("https://pyris.example.com");
        assertThat(iris.get("secret-token")).isEqualTo("${ARTEMIS_IRIS_SECRET_TOKEN}");
        @SuppressWarnings("unchecked")
        Map<String, Object> ratelimit = (Map<String, Object>) iris.get("ratelimit");
        assertThat(ratelimit.get("default-limit")).isEqualTo(-1);
        @SuppressWarnings("unchecked")
        Map<String, Object> lecture = (Map<String, Object>) artemis.get("lecture");
        assertThat(lecture.get("enabled")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> atlas = (Map<String, Object>) artemis.get("atlas");
        assertThat(atlas.get("temperature")).isEqualTo(0.8);
        @SuppressWarnings("unchecked")
        Map<String, Object> spring = (Map<String, Object>) parsed.get("spring");
        @SuppressWarnings("unchecked")
        Map<String, Object> ai = (Map<String, Object>) spring.get("ai");
        @SuppressWarnings("unchecked")
        Map<String, Object> openai = (Map<String, Object>) ai.get("openai");
        assertThat(openai.get("timeout")).isEqualTo("5m");
    }

    @Test
    void emitsBooleansAndEnvironmentPlaceholdersUnquoted() {
        String yaml = writer.write(List.of(new OverlayEntry("artemis.iris.enabled", Boolean.TRUE), new OverlayEntry("artemis.iris.secret-token", "${ARTEMIS_IRIS_SECRET_TOKEN}")));

        assertThat(yaml).contains("enabled: true");
        assertThat(yaml).contains("secret-token: ${ARTEMIS_IRIS_SECRET_TOKEN}");
        assertThat(yaml).doesNotContain("\"${ARTEMIS_IRIS_SECRET_TOKEN}\"");
    }

    @Test
    void returnsEmptyStringForNoEntries() {
        assertThat(writer.write(List.of())).isEmpty();
    }
}
