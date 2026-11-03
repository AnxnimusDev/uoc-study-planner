package edu.uoc.planner;

import static org.assertj.core.api.Assertions.assertThat;

import edu.uoc.planner.planning.PlannedSession;
import edu.uoc.planner.scheduling.PlanReport;
import edu.uoc.planner.scheduling.PlanRun;
import edu.uoc.planner.scheduling.internal.ProgressStore;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** ADR 0011: reported progress → plan, and dropped when the statement is extracted again. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "planner.availability.file=../config/availability.example.yml")
class TaskProgressIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-09-24T06:00:00Z");

  @Autowired private PlanRun planRun;
  @Autowired private ProgressStore progress;
  @Autowired private JdbcTemplate jdbc;

  private long r1;

  @BeforeEach
  void setUp() {
    jdbc.execute(
        "TRUNCATE task_progress, plan_notice, study_session, plan_version, task, canvas_page, deliverable, challenge_spec, challenge, course"
            + " RESTART IDENTITY CASCADE");
    jdbc.update(
        "INSERT INTO course (canvas_id, code, name, term_id, selected, start_at) VALUES (94063, '05.557 - Àlgebra - Aula 2', 'Àlgebra', 220, true, ?)",
        Timestamp.from(Instant.parse("2026-09-22T22:00:00Z")));
    r1 =
        jdbc.queryForObject(
            "INSERT INTO challenge (course_id, canvas_module_id, position, name, weight_pct, status) VALUES (94063, 565757, 3, 'Repte 1', 15, 'EXTRACTED') RETURNING id",
            Long.class);
    jdbc.update(
        "INSERT INTO deliverable (canvas_assignment_id, challenge_id, name, unlock_at, due_at, points, counts_for_grade, submission_types) VALUES (979687, ?, 'QÜESTIONARI 1 - Setmana 2', ?, ?, 15, true, ARRAY['external_tool'])",
        r1,
        Timestamp.from(Instant.parse("2026-09-22T22:00:00Z")),
        Timestamp.from(Instant.parse("2026-10-02T21:59:59Z")));
    spec(1);
  }

  @Test
  void plansOnlyWhatIsLeft() {
    progress.save(new ProgressStore.Entry("1:t1", r1, 1, 0, NOW));
    progress.save(new ProgressStore.Entry("1:t2", r1, 1, 120, NOW));
    progress.save(new ProgressStore.Entry("1:t2", r1, 1, 60, NOW)); // replaces the previous one

    PlanReport report = planRun.run(NOW);

    assertThat(sessionsOf(report, "1:t1")).isEmpty();
    assertThat(total(report, "1:t2")).isEqualTo(Duration.ofMinutes(60));
    assertThat(report.progressReset()).isEmpty();
    assertThat(progress.all()).hasSize(2);
  }

  @Test
  void dropsTheProgressOfAStatementExtractedAgain() {
    progress.save(new ProgressStore.Entry("1:t1", r1, 1, 0, NOW));
    progress.save(new ProgressStore.Entry("quiz:979687", r1, null, 0, NOW));
    spec(2);

    PlanReport report = planRun.run(NOW);

    assertThat(report.progressReset()).containsExactly("05.557 · Repte 1");
    assertThat(sessionsOf(report, "1:t1")).isNotEmpty();
    assertThat(progress.all())
        .extracting(ProgressStore.Entry::taskId)
        .containsExactly("quiz:979687");
  }

  @Test
  void listsTheTasksProgressCanBeRecordedOn() {
    assertThat(planRun.currentTasks(NOW)).extracting(t -> t.id()).contains("1:t1", "1:t3");
    assertThat(planRun.specVersion(r1)).contains(1);
  }

  private void spec(int version) {
    jdbc.update(
        "INSERT INTO challenge_spec (challenge_id, version, spec_json, model, source_hash) VALUES (?, ?, CAST(? AS jsonb), 'claude-opus-5-5', ?)",
        r1,
        version,
        Fixtures.json("claude/algebra-r1-spec.json"),
        "h" + version);
  }

  private static java.util.List<PlannedSession> sessionsOf(PlanReport report, String taskId) {
    return report.plan().sessions().stream().filter(s -> s.taskId().equals(taskId)).toList();
  }

  private static Duration total(PlanReport report, String taskId) {
    return sessionsOf(report, taskId).stream()
        .map(PlannedSession::duration)
        .reduce(Duration.ZERO, Duration::plus);
  }
}
