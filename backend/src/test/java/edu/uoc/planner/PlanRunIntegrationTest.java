package edu.uoc.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.uoc.planner.output.CalendarSnapshot;
import edu.uoc.planner.output.CalendarSource;
import edu.uoc.planner.planning.PlannedSession;
import edu.uoc.planner.scheduling.PlanReport;
import edu.uoc.planner.scheduling.PlanRun;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Stored challenges and specs → plan → database → calendar feed, with the example profile. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "planner.availability.file=../config/availability.example.yml")
class PlanRunIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-09-24T06:00:00Z");

  @Autowired private PlanRun planRun;
  @Autowired private CalendarSource calendar;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MockMvc mvc;

  private long r1;
  private long r2;

  @BeforeEach
  void setUp() {
    jdbc.execute(
        "TRUNCATE plan_notice, study_session, plan_version, task, canvas_page, deliverable, challenge_spec, challenge, course"
            + " RESTART IDENTITY CASCADE");
    jdbc.update(
        "INSERT INTO course (canvas_id, code, name, term_id, selected, start_at) VALUES (94063, '05.557 - Àlgebra - Aula 2', 'Àlgebra', 220, true, ?)",
        Timestamp.from(Instant.parse("2026-09-22T22:00:00Z")));
    r1 = challenge(565757, 3, "Repte 1");
    deliverable(
        979687,
        r1,
        "QÜESTIONARI 1 - Setmana 2",
        "2026-09-22T22:00:00Z",
        "2026-10-02T21:59:59Z",
        15,
        true,
        "external_tool");
    deliverable(
        979674,
        r1,
        "Enregistraments R1",
        "2026-10-06T22:00:00Z",
        "2026-10-11T21:59:59Z",
        0,
        false,
        "media_recording");
    jdbc.update(
        "INSERT INTO challenge_spec (challenge_id, version, spec_json, model, source_hash) VALUES (?, 1, CAST(? AS jsonb), 'claude-opus-5-5', 'h')",
        r1,
        Fixtures.json("claude/algebra-r1-spec.json"));
    r2 = challenge(565760, 4, "Repte 2");
  }

  @Test
  void plansEveryTaskWithoutOverlapsAndBeforeItsMargin() {
    PlanReport report = planRun.run(NOW);

    List<PlannedSession> sessions = report.plan().sessions();
    assertThat(sessions).extracting(PlannedSession::taskId).contains("1:t1", "1:t2", "1:t3");
    assertThat(report.plan().atRisk()).isEmpty();
    List<PlannedSession> sorted =
        sessions.stream().sorted(Comparator.comparing(PlannedSession::start)).toList();
    for (int i = 1; i < sorted.size(); i++) {
      assertThat(sorted.get(i).start()).isAfterOrEqualTo(sorted.get(i - 1).end());
    }
    Instant quizMargin = Instant.parse("2026-10-01T21:59:59Z");
    assertThat(sessions)
        .filteredOn(s -> s.taskId().equals("1:t3"))
        .allSatisfy(s -> assertThat(s.end()).isBeforeOrEqualTo(quizMargin));
    assertThat(report.plan().notices()).extracting(n -> n.taskId()).contains("1:t4");
  }

  @Test
  void storesThePlanAndServesItToTheCalendars() throws Exception {
    planRun.run(NOW);

    CalendarSnapshot snapshot = calendar.current();
    assertThat(snapshot.sessions()).isNotEmpty();
    assertThat(snapshot.deadlines()).extracting(d -> d.canvasAssignmentId()).contains(979687L);
    assertThat(snapshot.notices()).isNotEmpty();

    String token = feedToken();
    mvc.perform(get("/calendar/" + token + "/plan.ics"))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("session-1-t1-1@uoc-study-planner")));
    mvc.perform(get("/calendar/" + token + "/entregas.ics"))
        .andExpect(
            content().string(Matchers.containsString("deliverable-979687@uoc-study-planner")));
  }

  @Test
  void marksAndClearsTheRiskOnTheChallenge() {
    deliverable(
        979698,
        r2,
        "QÜESTIONARI 2 - Setmana 3",
        "2026-09-22T22:00:00Z",
        "2026-09-24T07:00:00Z",
        3.75,
        true,
        "external_tool");

    planRun.run(NOW);
    assertThat(challengeStatus(r2)).isEqualTo("AT_RISK");

    jdbc.update(
        "UPDATE deliverable SET due_at = ? WHERE canvas_assignment_id = 979698",
        Timestamp.from(Instant.parse("2026-10-30T22:59:59Z")));
    planRun.run(NOW);
    assertThat(challengeStatus(r2)).isEqualTo("SYNCED");
    assertThat(challengeStatus(r1)).isEqualTo("EXTRACTED");
  }

  @Test
  void reportsWhatItCannotEstimate() {
    deliverable(
        968028,
        r2,
        "Lliurament de l'activitat R2",
        null,
        "2026-10-25T22:59:59Z",
        25,
        true,
        "online_upload");

    assertThat(planRun.run(NOW).unestimated())
        .containsExactly("05.557 · Lliurament de l'activitat R2");
  }

  private String feedToken() throws Exception {
    // The token is created on first use; the feed URL command prints it the same way.
    var tokens = Class.forName("edu.uoc.planner.output.internal.FeedTokenStore");
    return (String)
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            applicationContext.getBean(tokens), "token");
  }

  @Autowired private org.springframework.context.ApplicationContext applicationContext;

  private long challenge(long moduleId, int position, String name) {
    return jdbc.queryForObject(
        "INSERT INTO challenge (course_id, canvas_module_id, position, name, weight_pct, status) VALUES (94063, ?, ?, ?, 15, ?) RETURNING id",
        Long.class,
        moduleId,
        position,
        name,
        moduleId == 565757 ? "EXTRACTED" : "SYNCED");
  }

  private void deliverable(
      long id,
      long challengeId,
      String name,
      String unlock,
      String due,
      double points,
      boolean counts,
      String type) {
    jdbc.update(
        "INSERT INTO deliverable (canvas_assignment_id, challenge_id, name, unlock_at, due_at, points, counts_for_grade, submission_types) VALUES (?, ?, ?, ?, ?, ?, ?, ARRAY[?])",
        id,
        challengeId,
        name,
        unlock == null ? null : Timestamp.from(Instant.parse(unlock)),
        Timestamp.from(Instant.parse(due)),
        points,
        counts,
        type);
  }

  private String challengeStatus(long challengeId) {
    return jdbc.queryForObject(
        "SELECT status FROM challenge WHERE id = ?", String.class, challengeId);
  }
}
