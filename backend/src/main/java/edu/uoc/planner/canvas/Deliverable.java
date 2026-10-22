package edu.uoc.planner.canvas;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A Canvas assignment of a challenge, resolved through the module item {@code content_id}.
 *
 * @param points {@code points_possible}; zero when Canvas sends none
 * @param countsForGrade {@code !omit_from_final_grade}
 */
public record Deliverable(
    long canvasAssignmentId,
    String name,
    Instant unlockAt,
    Instant dueAt,
    Instant lockAt,
    BigDecimal points,
    boolean countsForGrade,
    List<String> submissionTypes) {

  public Deliverable {
    submissionTypes = List.copyOf(submissionTypes);
  }

  /** Wiris quizzes: no readable content, planned with a default duration. */
  public boolean externalTool() {
    return submissionTypes.equals(List.of("external_tool"));
  }
}
