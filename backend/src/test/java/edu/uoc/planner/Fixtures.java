package edu.uoc.planner;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Loads anonymized fixtures from {@code src/test/resources/fixtures}. */
public final class Fixtures {

  private static final String CANVAS_JSON_PREFIX = "while(1);";

  private Fixtures() {}

  /**
   * Returns the JSON of a fixture without the {@code while(1);} prefix Canvas adds to cookie
   * authenticated responses.
   *
   * @param path relative to {@code fixtures/}, e.g. {@code canvas/algebra-modules.json}
   */
  public static String json(String path) {
    String content = read(path);
    return content.startsWith(CANVAS_JSON_PREFIX)
        ? content.substring(CANVAS_JSON_PREFIX.length())
        : content;
  }

  private static String read(String path) {
    try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + path)) {
      if (in == null) {
        throw new IllegalArgumentException("Fixture not found: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
