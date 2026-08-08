package de.tum.cit.aet.artemis.featuremodel.extraction.artifact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.ObjectMapper;

/** Writes extraction-owned JSON with the established two-space, LF-terminated byte contract. */
public final class ExtractionJsonWriter {

    private static final String LINE_FEED = "\n";

    private final ObjectMapper objectMapper;

    private final DefaultPrettyPrinter prettyPrinter;

    /**
     * Creates a writer backed by the command's shared Jackson mapper.
     *
     * @param objectMapper mapper used for serialization.
     */
    public ExtractionJsonWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        DefaultIndenter indenter = new DefaultIndenter("  ", LINE_FEED);
        prettyPrinter = new DefaultPrettyPrinter().withObjectIndenter(indenter).withArrayIndenter(indenter);
    }

    /**
     * Serializes one payload deterministically and writes a trailing line feed.
     *
     * @param file target artifact file; its parent must already exist.
     * @param payload payload to serialize.
     * @throws IOException if serialization or writing fails.
     */
    public void write(Path file, Object payload) throws IOException {
        String json = objectMapper.writer().with(prettyPrinter).writeValueAsString(payload);
        Files.write(file, (json + LINE_FEED).getBytes(StandardCharsets.UTF_8));
    }
}
