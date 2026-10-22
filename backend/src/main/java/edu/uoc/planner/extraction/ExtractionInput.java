package edu.uoc.planner.extraction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * What is sent to Claude for one challenge. ADR 0003: statement, approach, study guide and rubric,
 * plus the module deliverables. Never forums, names nor grades.
 *
 * @param courseId current course, so links to other courses can be flagged as stale
 * @param approachHtml "Plantejament", optional
 * @param studyGuideHtml weekly study guide (GES), optional
 * @param rubric rubric text, optional
 */
public record ExtractionInput(
    long courseId,
    String challengeTitle,
    String statementHtml,
    String approachHtml,
    String studyGuideHtml,
    String rubric,
    List<Deliverable> deliverables) {

  public ExtractionInput {
    if (statementHtml == null || statementHtml.isBlank()) {
      throw new IllegalArgumentException("A statement is required");
    }
    deliverables = deliverables == null ? List.of() : List.copyOf(deliverables);
  }

  /**
   * @param ref {@code assignment:<id>} or {@code discussion:<id>}: the only references the spec may
   *     use
   */
  public record Deliverable(
      String ref, String name, Instant dueAt, BigDecimal points, boolean countsForGrade) {}
}
