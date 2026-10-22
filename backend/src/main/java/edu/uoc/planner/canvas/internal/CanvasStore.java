package edu.uoc.planner.canvas.internal;

import edu.uoc.planner.canvas.Challenge;
import edu.uoc.planner.canvas.Course;
import edu.uoc.planner.canvas.Deliverable;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Upserts of the Canvas mirror ({@code course}, {@code challenge}, {@code deliverable}, {@code
 * canvas_page}). The student's choices ({@code selected}, {@code margin_days}) and the challenge
 * status are never overwritten by a sync.
 */
@Component
class CanvasStore {

  enum PageRole {
    STATEMENT,
    STUDY_GUIDE,
    APPROACH
  }

  private final JdbcClient jdbc;

  CanvasStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** New courses start selected: until the UI exists there is no other way to choose. */
  void upsertCourse(Course course, String icsUrl) {
    jdbc.sql(
            """
            INSERT INTO course (canvas_id, code, name, term_id, ics_url, start_at, selected)
            VALUES (:id, :code, :name, :term, :ics, :start, true)
            ON CONFLICT (canvas_id) DO UPDATE
              SET code = EXCLUDED.code, name = EXCLUDED.name, term_id = EXCLUDED.term_id,
                  ics_url = EXCLUDED.ics_url, start_at = EXCLUDED.start_at
            """)
        .param("id", course.canvasId())
        .param("code", Objects.requireNonNullElse(course.code(), course.name()))
        .param("name", course.name())
        .param("term", course.termId())
        .param("ics", icsUrl)
        .param("start", timestamp(course.startAt()))
        .update();
  }

  Set<Long> selectedCourses() {
    return Set.copyOf(
        jdbc.sql("SELECT canvas_id FROM course WHERE selected").query(Long.class).list());
  }

  /**
   * @return the challenge id
   */
  long upsertChallenge(Challenge challenge) {
    return jdbc.sql(
            """
            INSERT INTO challenge (course_id, canvas_module_id, position, name, weight_pct, status)
            VALUES (:course, :module, :position, :name, :weight, 'SYNCED')
            ON CONFLICT (canvas_module_id) DO UPDATE
              SET course_id = EXCLUDED.course_id, position = EXCLUDED.position,
                  name = EXCLUDED.name, weight_pct = EXCLUDED.weight_pct
            RETURNING id
            """)
        .param("course", challenge.courseId())
        .param("module", challenge.canvasModuleId())
        .param("position", challenge.position())
        .param("name", challenge.name())
        .param("weight", challenge.weightPct())
        .query(Long.class)
        .single();
  }

  void upsertDeliverable(long challengeId, Deliverable d) {
    jdbc.sql(
            """
            INSERT INTO deliverable (canvas_assignment_id, challenge_id, name, unlock_at, due_at,
                                     lock_at, points, counts_for_grade, submission_types)
            VALUES (:id, :challenge, :name, :unlock, :due, :lock, :points, :counts,
                    string_to_array(:types, ','))
            ON CONFLICT (canvas_assignment_id) DO UPDATE
              SET challenge_id = EXCLUDED.challenge_id, name = EXCLUDED.name,
                  unlock_at = EXCLUDED.unlock_at, due_at = EXCLUDED.due_at,
                  lock_at = EXCLUDED.lock_at, points = EXCLUDED.points,
                  counts_for_grade = EXCLUDED.counts_for_grade,
                  submission_types = EXCLUDED.submission_types
            """)
        .param("id", d.canvasAssignmentId())
        .param("challenge", challengeId)
        .param("name", d.name())
        .param("unlock", timestamp(d.unlockAt()))
        .param("due", timestamp(d.dueAt()))
        .param("lock", timestamp(d.lockAt()))
        .param("points", d.points())
        .param("counts", d.countsForGrade())
        .param("types", String.join(",", d.submissionTypes()))
        .update();
  }

  /**
   * @return true when the page is new or its body changed
   */
  boolean upsertPage(
      long courseId,
      long challengeId,
      String pageUrl,
      String title,
      PageRole role,
      String body,
      String bodyHash,
      Instant updatedAt) {
    Optional<String> previous =
        jdbc.sql("SELECT body_hash FROM canvas_page WHERE course_id = :course AND page_url = :url")
            .param("course", courseId)
            .param("url", pageUrl)
            .query(String.class)
            .optional();
    jdbc.sql(
            """
            INSERT INTO canvas_page (course_id, page_url, challenge_id, title, role, body_html,
                                     body_hash, updated_at)
            VALUES (:course, :url, :challenge, :title, :role, :body, :hash, :updated)
            ON CONFLICT (course_id, page_url) DO UPDATE
              SET challenge_id = EXCLUDED.challenge_id, title = EXCLUDED.title,
                  role = EXCLUDED.role, body_html = EXCLUDED.body_html,
                  body_hash = EXCLUDED.body_hash, updated_at = EXCLUDED.updated_at
            """)
        .param("course", courseId)
        .param("url", pageUrl)
        .param("challenge", challengeId)
        .param("title", title)
        .param("role", role.name())
        .param("body", body)
        .param("hash", bodyHash)
        .param("updated", timestamp(updatedAt))
        .update();
    return previous.map(h -> !h.equals(bodyHash)).orElse(true);
  }

  private static Timestamp timestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }
}
