package edu.uoc.planner.extraction.internal;

import edu.uoc.planner.extraction.ExtractionInput;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** System prompt and user messages for the extractor. Labels in Spanish, like the prompt. */
public final class ExtractionPrompts {

  private static final String SYSTEM_PROMPT = "/prompts/extraction-system.md";
  private static final int MAX_ERRORS_IN_RETRY = 20;

  private ExtractionPrompts() {}

  public static String system() {
    try (InputStream in = ExtractionPrompts.class.getResourceAsStream(SYSTEM_PROMPT)) {
      if (in == null) {
        throw new IllegalStateException("Missing " + SYSTEM_PROMPT + " on the classpath");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Deterministic for the same input: its hash decides whether to extract again. */
  public static String user(ExtractionInput input) {
    StringBuilder message = new StringBuilder();
    message.append("Curso actual: ").append(input.courseId()).append('\n');
    message.append("Reto: ").append(input.challengeTitle()).append("\n\n");
    message.append("## Entregas del módulo\n");
    if (input.deliverables().isEmpty()) {
      message.append("(ninguna)\n");
    }
    for (ExtractionInput.Deliverable d : input.deliverables()) {
      message.append("- ").append(d.ref()).append(" · ").append(d.name());
      message.append(" · vence ").append(d.dueAt() == null ? "sin fecha" : d.dueAt().toString());
      if (d.points() != null) {
        message
            .append(" · ")
            .append(d.points().stripTrailingZeros().toPlainString())
            .append(" puntos");
      }
      message.append(d.countsForGrade() ? " · cuenta para la nota" : " · no cuenta para la nota");
      message.append('\n');
    }
    section(message, "Enunciado", HtmlCleaner.clean(input.statementHtml()));
    section(message, "Planteamiento", HtmlCleaner.clean(input.approachHtml()));
    section(message, "Guía de estudio semanal (GES)", HtmlCleaner.clean(input.studyGuideHtml()));
    section(message, "Rúbrica", input.rubric() == null ? "" : input.rubric().strip());
    return message.toString();
  }

  /**
   * A fresh single-turn request: the previous answer and its problems go in the user message, so no
   * earlier assistant turn is replayed (ADR 0006).
   */
  public static String retry(String userMessage, String previousAnswer, List<String> errors) {
    StringBuilder message = new StringBuilder(userMessage);
    message.append("\n## Intento anterior\n");
    message.append("Un intento anterior devolvió este JSON, que no es válido:\n\n```json\n");
    message.append(previousAnswer.strip()).append("\n```\n\nProblemas:\n");
    errors.stream()
        .limit(MAX_ERRORS_IN_RETRY)
        .forEach(e -> message.append("- ").append(e).append('\n'));
    message.append("\nDevuelve el ChallengeSpec completo y corregido.\n");
    return message.toString();
  }

  private static void section(StringBuilder message, String title, String content) {
    if (!content.isBlank()) {
      message.append("\n## ").append(title).append('\n').append(content).append('\n');
    }
  }
}
