package edu.uoc.planner.planning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import edu.uoc.planner.TestcontainersConfiguration;
import edu.uoc.planner.extraction.ChallengeSpec.TaskKind;
import edu.uoc.planner.planning.AtRiskTask;
import edu.uoc.planner.planning.Notice;
import edu.uoc.planner.planning.Plan;
import edu.uoc.planner.planning.PlanStore;
import edu.uoc.planner.planning.PlanTask;
import edu.uoc.planner.planning.PlanTrigger;
import edu.uoc.planner.planning.PlannedSession;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, PlanStore.class})
class PlanStoreTest {

  private static final Instant NOW = Instant.parse("2026-10-26T07:00:00Z");

  @Autowired private PlanStore store;
  @Autowired private JdbcTemplate jdbc;

  private long challengeId;

  @BeforeEach
  void setUp() {
    jdbc.update(
        "INSERT INTO course (canvas_id, code, name, term_id, selected) VALUES (94063, '05.557 - Àlgebra - Aula 2', 'Àlgebra', 220, true)");
    challengeId =
        jdbc.queryForObject(
            "INSERT INTO challenge (course_id, canvas_module_id, position, name, weight_pct, status) VALUES (94063, 565757, 3, 'Repte 1', 15, 'EXTRACTED') RETURNING id",
            Long.class);
  }

  @Test
  void storesTheVersionTasksSessionsAndNotices() {
    long version = store.save(plan(session("1:t1", 17, 18)), tasks(), PlanTrigger.MANUAL, NOW);

    assertThat(
            jdbc.queryForObject(
                "SELECT trigger FROM plan_version WHERE id = ?", String.class, version))
        .isEqualTo("MANUAL");
    assertThat(
            jdbc.queryForObject(
                "SELECT unscheduled_hours FROM plan_version WHERE id = ?",
                BigDecimal.class,
                version))
        .isEqualByComparingTo("1.5");
    assertThat(
            jdbc.queryForList("SELECT spec_task_id FROM task ORDER BY spec_task_id", String.class))
        .containsExactly("1:t1", "1:t2");
    assertThat(
            jdbc.queryForObject(
                "SELECT cardinality(depends_on) FROM task WHERE spec_task_id = '1:t2'",
                Integer.class))
        .isEqualTo(1);
    assertThat(store.latestSessions())
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.taskId()).isEqualTo("1:t1");
              assertThat(s.courseCode()).isEqualTo("05.557");
              assertThat(s.challengeId()).isEqualTo(challengeId);
              assertThat(s.kind()).isEqualTo(TaskKind.READING);
            });
    assertThat(store.latestNotices()).extracting(Notice::taskId).containsExactly("1:t2");
  }

  @Test
  void keepsOnlyTheLatestPlansSessions() {
    store.save(plan(session("1:t1", 17, 18)), tasks(), PlanTrigger.MANUAL, NOW);
    store.save(plan(session("1:t1", 19, 20)), tasks(), PlanTrigger.SYNC, NOW.plusSeconds(60));

    assertThat(store.latestSessions())
        .singleElement()
        .satisfies(s -> assertThat(s.start()).isEqualTo(NOW.plus(Duration.ofHours(19))));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM task", Integer.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM plan_version", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void keepsTheSessionsOfTheNextTwoDays() {
    store.save(
        plan(session("1:t1", 10, 11), session("1:t2", 30, 31), session("1:t2", 60, 61)),
        tasks(),
        PlanTrigger.MANUAL,
        NOW);

    assertThat(store.sessionsToKeep(NOW, Duration.ofDays(2)))
        .extracting(PlannedSession::taskId)
        .containsExactly("1:t1", "1:t2");
    assertThat(store.sessionsToKeep(NOW, Duration.ofDays(2)))
        .allSatisfy(s -> assertThat(s.kept()).isTrue());
  }

  private Plan plan(PlannedSession... sessions) {
    return new Plan(
        List.of(sessions),
        List.of(
            new AtRiskTask("1:t2", challengeId, Duration.ofMinutes(90), "No cabe antes del plazo")),
        List.of(
            new Notice(
                "1:t2",
                Notice.Type.VIDEO_WINDOW,
                NOW,
                NOW.plus(Duration.ofDays(5)),
                "Graba el vídeo")),
        List.of(),
        List.of(challengeId),
        Duration.ofMinutes(90));
  }

  private PlannedSession session(String taskId, int fromHours, int toHours) {
    return new PlannedSession(
        taskId,
        challengeId,
        "05.557",
        "Tasca " + taskId,
        TaskKind.READING,
        NOW.plus(Duration.ofHours(fromHours)),
        NOW.plus(Duration.ofHours(toHours)),
        false);
  }

  private List<PlanTask> tasks() {
    return List.of(
        new PlanTask(
            "1:t1",
            challengeId,
            "05.557",
            15,
            0,
            "Llegir",
            TaskKind.READING,
            Duration.ofHours(1),
            null,
            NOW.plus(Duration.ofDays(5)),
            1,
            null,
            List.of(),
            0,
            null,
            false),
        new PlanTask(
            "1:t2",
            challengeId,
            "05.557",
            15,
            1,
            "Gravar",
            TaskKind.VIDEO_RECORDING,
            Duration.ofMinutes(30),
            null,
            NOW.plus(Duration.ofDays(5)),
            1,
            null,
            List.of("1:t1"),
            0,
            null,
            false));
  }
}
