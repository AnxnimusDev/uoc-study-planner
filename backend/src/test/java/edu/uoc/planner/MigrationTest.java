package edu.uoc.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@JdbcTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class MigrationTest {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void createsEveryTableOfTheDataModel() {
    List<String> tables =
        jdbc.queryForList(
            """
            SELECT table_name FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
            """,
            String.class);

    assertThat(tables)
        .containsExactlyInAnyOrder(
            "course",
            "challenge",
            "canvas_page",
            "deliverable",
            "challenge_spec",
            "task",
            "study_session",
            "plan_version",
            "availability_block",
            "availability_override",
            "checkin",
            "external_ref",
            "user_settings",
            "session_secret");
  }

  @Test
  void storesInstantsAsTimestamptz() {
    assertThat(columnsOfType("timestamp without time zone")).isEmpty();
    assertThat(columnsOfType("timestamp with time zone"))
        .contains(
            "deliverable.unlock_at",
            "deliverable.due_at",
            "deliverable.lock_at",
            "task.soft_deadline",
            "task.hard_deadline",
            "study_session.starts_at",
            "study_session.ends_at",
            "session_secret.captured_at");
  }

  @Test
  void usesJsonbOnlyForSpecRubricAndCheckinAnswers() {
    assertThat(columnsOfType("jsonb"))
        .containsExactlyInAnyOrder(
            "challenge_spec.spec_json", "deliverable.rubric_json", "checkin.answers_json");
  }

  @Test
  void storesSecretsAsBinary() {
    assertThat(columnsOfType("bytea"))
        .containsExactlyInAnyOrder("session_secret.cookie_blob", "user_settings.value");
  }

  @Test
  void acceptsKnownChallengeStatus() {
    insertCourse(1L);

    jdbc.update(
        "INSERT INTO challenge (course_id, canvas_module_id, position, name, status)"
            + " VALUES (1, 10, 1, 'Repte 1', 'SYNCED')");

    assertThat(jdbc.queryForObject("SELECT count(*) FROM challenge", Integer.class)).isOne();
  }

  @Test
  void rejectsUnknownChallengeStatus() {
    insertCourse(1L);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO challenge (course_id, canvas_module_id, position, name, status)"
                        + " VALUES (1, 10, 1, 'Repte 1', 'UNKNOWN')"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsUnknownTaskKind() {
    insertCourse(1L);
    jdbc.update(
        "INSERT INTO challenge (id, course_id, canvas_module_id, position, name, status)"
            + " OVERRIDING SYSTEM VALUE VALUES (1, 1, 10, 1, 'Repte 1', 'SYNCED')");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO task (challenge_id, title, kind, estimated_hours)"
                        + " VALUES (1, 'Llegir', 'SLEEPING', 1.5)"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsChallengeOfUnknownCourse() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO challenge (course_id, canvas_module_id, position, name, status)"
                        + " VALUES (999, 10, 1, 'Repte 1', 'SYNCED')"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void insertCourse(long canvasId) {
    jdbc.update(
        "INSERT INTO course (canvas_id, code, name, term_id) VALUES (?, 'C', 'Curs', 220)",
        canvasId);
  }

  private List<String> columnsOfType(String dataType) {
    return jdbc.queryForList(
        """
        SELECT table_name || '.' || column_name FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
          AND data_type = ?
        """,
        String.class,
        dataType);
  }
}
