package edu.uoc.planner.canvas;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * A challenge ("Repte"): one Canvas module with its pages and deliverables.
 *
 * @param position module position in the course
 * @param weightPct share of the course grade, 0–100 with two decimals; zero when nothing in the
 *     course has points
 * @param unresolvedAssignmentIds assignments the module links to but the course list did not
 *     return; the sync has to fetch them one by one
 */
public record Challenge(
    long canvasModuleId,
    long courseId,
    int position,
    String name,
    List<ChallengePage> pages,
    List<Deliverable> deliverables,
    BigDecimal weightPct,
    List<Long> unresolvedAssignmentIds) {

  public Challenge {
    pages = List.copyOf(pages);
    deliverables = List.copyOf(deliverables);
    unresolvedAssignmentIds = List.copyOf(unresolvedAssignmentIds);
  }

  public Optional<Statement> statement() {
    return pages.stream()
        .filter(Statement.class::isInstance)
        .map(Statement.class::cast)
        .findFirst();
  }

  public Optional<StudyGuide> studyGuide() {
    return pages.stream()
        .filter(StudyGuide.class::isInstance)
        .map(StudyGuide.class::cast)
        .findFirst();
  }
}
