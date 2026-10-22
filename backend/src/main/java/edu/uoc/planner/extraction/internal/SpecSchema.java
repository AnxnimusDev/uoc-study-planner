package edu.uoc.planner.extraction.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Set;

/**
 * {@code docs/challenge-spec.schema.json}, copied to {@code schemas/} on the classpath by the
 * build.
 */
final class SpecSchema {

  private static final String RESOURCE = "/schemas/challenge-spec.schema.json";

  /**
   * Keywords structured outputs does not accept. They stay in the full schema, which is what the
   * answer is validated against locally (ADR 0006).
   */
  private static final Set<String> UNSUPPORTED_BY_API =
      Set.of(
          "$schema",
          "$id",
          "minimum",
          "maximum",
          "exclusiveMinimum",
          "exclusiveMaximum",
          "multipleOf",
          "minLength",
          "maxLength",
          "pattern");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private SpecSchema() {}

  static JsonNode full() {
    try (InputStream in = SpecSchema.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing " + RESOURCE + " on the classpath");
      }
      return MAPPER.readTree(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The schema sent as {@code output_config.format.schema}: same shape, fewer constraints. */
  static JsonNode forApi() {
    JsonNode schema = full().deepCopy();
    strip(schema);
    return schema;
  }

  private static void strip(JsonNode node) {
    if (node instanceof ObjectNode object) {
      object.remove(UNSUPPORTED_BY_API);
      JsonNode constant = object.remove("const");
      if (constant != null) {
        object.set("enum", MAPPER.createArrayNode().add(constant));
      }
      object.forEach(SpecSchema::strip);
    } else if (node instanceof ArrayNode array) {
      array.forEach(SpecSchema::strip);
    }
  }
}
