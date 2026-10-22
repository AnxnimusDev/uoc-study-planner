package edu.uoc.planner.canvas;

import java.math.BigDecimal;
import java.util.List;

/**
 * A challenge as stored by the last sync, with what the extractor and the planner need.
 *
 * @param id {@code challenge.id}
 * @param courseCode Canvas {@code course_code}, e.g. "05.557 - Àlgebra - Aula 2"
 * @param courseStartAt first day of the course; may be null
 * @param statementHtml null when the statement page could not be read
 * @param approachHtml "Plantejament", may be null
 * @param studyGuideHtml weekly study guide (GES), may be null
 */
public record StoredChallenge(
    long id,
    long courseId,
    String courseCode,
    java.time.Instant courseStartAt,
    int position,
    String name,
    BigDecimal weightPct,
    ChallengeStatus status,
    String statementHtml,
    String approachHtml,
    String studyGuideHtml,
    List<Deliverable> deliverables) {

  public StoredChallenge {
    deliverables = List.copyOf(deliverables);
  }

  /** "05.557 - Àlgebra - Aula 2" → "05.557", the code margins and calendars use. */
  public String courseShortCode() {
    int dash = courseCode.indexOf(" - ");
    return dash < 0 ? courseCode.strip() : courseCode.substring(0, dash).strip();
  }
}
