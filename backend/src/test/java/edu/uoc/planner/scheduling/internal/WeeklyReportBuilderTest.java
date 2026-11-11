package edu.uoc.planner.scheduling.internal;

import static org.assertj.core.api.Assertions.assertThat;

import edu.uoc.planner.canvas.ChallengeStatus;
import edu.uoc.planner.canvas.Deliverable;
import edu.uoc.planner.canvas.StoredChallenge;
import edu.uoc.planner.extraction.ChallengeSpec;
import edu.uoc.planner.extraction.ChallengeSpec.AiPolicy;
import edu.uoc.planner.extraction.ChallengeSpec.AiPolicyLevel;
import edu.uoc.planner.extraction.ChallengeSpec.DeliverableType;
import edu.uoc.planner.extraction.ChallengeSpec.TaskKind;
import edu.uoc.planner.output.WeeklyReport;
import edu.uoc.planner.output.WeeklyReport.ChallengeAtRisk;
import edu.uoc.planner.output.WeeklyReport.ChallengeRules;
import edu.uoc.planner.planning.AtRiskTask;
import edu.uoc.planner.planning.Plan;
import edu.uoc.planner.planning.PlannedSession;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeeklyReportBuilderTest {

  private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

  /** Thursday 5/11 at 19:00: the seven days end on Wednesday 11/11. */
  private static final Instant NOW = Instant.parse("2026-11-05T18:00:00Z");

  @Test
  void coversTheSevenDaysFromToday() {
    PlannedSession lastDay = session("1:t1", 1, "2026-11-11T22:00:00Z");
    PlannedSession nextWeek = session("1:t2", 1, "2026-11-11T23:30:00Z");

    WeeklyReport report = build(plan(List.of(lastDay, nextWeek), List.of()), List.of(), Map.of());

    assertThat(report.weekStart()).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(report.zone()).isEqualTo(MADRID);
    assertThat(report.sessions()).containsExactly(lastDay);
    assertThat(report.courseNames()).containsEntry("05.557", "Àlgebra");
  }

  @Test
  void listsOnlyTheDeadlinesStillToMeetThisWeek() {
    StoredChallenge challenge =
        challenge(
            1,
            "Repte 1",
            deliverable(10, "Vençut", "2026-11-05T17:00:00Z", false),
            deliverable(11, "Aquesta setmana", "2026-11-06T22:59:00Z", false),
            deliverable(12, "Ja lliurat", "2026-11-07T22:59:00Z", true),
            deliverable(13, "Més endavant", "2026-11-20T22:59:00Z", false),
            deliverable(14, "Sense data", null, false));

    WeeklyReport report = build(plan(List.of(), List.of()), List.of(challenge), Map.of());

    assertThat(report.deadlines())
        .singleElement()
        .satisfies(
            d -> {
              assertThat(d.canvasAssignmentId()).isEqualTo(11);
              assertThat(d.title()).isEqualTo("Aquesta setmana");
              assertThat(d.courseCode()).isEqualTo("05.557");
            });
  }

  @Test
  void addsUpWhatIsMissingByChallengeAndLeavesOutWeeklyTime() {
    List<AtRiskTask> risks =
        List.of(
            risk("2:t1", 2, 90),
            risk("1:t1", 1, 30),
            risk("2:t2", 2, 60),
            risk("weekly:05.557:2026-W45", 0, 45));

    WeeklyReport report =
        build(
            plan(List.of(), risks),
            List.of(challenge(1, "Repte 1"), challenge(2, "Repte 2")),
            Map.of());

    assertThat(report.atRisk())
        .containsExactly(
            new ChallengeAtRisk("Repte 2", "05.557", Duration.ofMinutes(150)),
            new ChallengeAtRisk("Repte 1", "05.557", Duration.ofMinutes(30)));
  }

  @Test
  void givesTheRulesOfTheChallengesWorkedOnOrDueThisWeek() {
    StoredChallenge worked = challenge(1, "Amb sessió");
    StoredChallenge due =
        challenge(2, "Amb entrega", deliverable(20, "PAC", "2026-11-06T22:59:00Z", false));
    StoredChallenge later = challenge(3, "Més endavant");
    StoredChallenge noSpec = challenge(4, "Sense enunciat");
    PlannedSession session = session("1:t1", 1, "2026-11-06T17:00:00Z");
    PlannedSession unextracted = session("4:t1", 4, "2026-11-06T19:00:00Z");
    PlannedSession weekly = session("weekly:05.557:2026-W45", 0, "2026-11-06T20:00:00Z");

    WeeklyReport report =
        build(
            plan(List.of(session, unextracted, weekly), List.of()),
            List.of(worked, due, later, noSpec),
            Map.of(
                1L, spec(AiPolicyLevel.FORBIDDEN, "No es permet la IA.", "PDF", "Màxim 5 pàgines"),
                2L, spec(AiPolicyLevel.LIMITED, "IA amb cites.", "PDF"),
                3L, spec(AiPolicyLevel.ALLOWED, "IA permesa.")));

    assertThat(report.rules())
        .containsExactly(
            new ChallengeRules(
                "Amb sessió",
                "05.557",
                AiPolicyLevel.FORBIDDEN,
                "No es permet la IA.",
                List.of("PDF", "Màxim 5 pàgines")),
            new ChallengeRules(
                "Amb entrega", "05.557", AiPolicyLevel.LIMITED, "IA amb cites.", List.of("PDF")));
  }

  @Test
  void aSpecWithoutPolicyStillHasItsFormatChecklist() {
    ChallengeSpec spec = spec(null, null, "PDF");

    WeeklyReport report =
        build(
            plan(List.of(session("1:t1", 1, "2026-11-06T17:00:00Z")), List.of()),
            List.of(challenge(1, "Repte 1")),
            Map.of(1L, spec));

    assertThat(report.rules())
        .containsExactly(new ChallengeRules("Repte 1", "05.557", null, null, List.of("PDF")));
  }

  private static WeeklyReport build(
      Plan plan, List<StoredChallenge> challenges, Map<Long, ChallengeSpec> specs) {
    return WeeklyReportBuilder.build(
        plan, MADRID, challenges, specs, Map.of("05.557", "Àlgebra"), NOW);
  }

  private static Plan plan(List<PlannedSession> sessions, List<AtRiskTask> risks) {
    return new Plan(sessions, risks, List.of(), List.of(), List.of(), Duration.ZERO);
  }

  private static PlannedSession session(String id, long challenge, String start) {
    Instant from = Instant.parse(start);
    return new PlannedSession(
        id, challenge, "05.557", "Tasca", TaskKind.WORK, from, from.plusSeconds(1800), false);
  }

  private static AtRiskTask risk(String id, long challenge, long minutes) {
    return new AtRiskTask(
        id, challenge, "05.557", "Tasca", null, Duration.ofMinutes(minutes), "No cabe");
  }

  private static Deliverable deliverable(long id, String name, String due, boolean submitted) {
    return new Deliverable(
        id,
        name,
        null,
        due == null ? null : Instant.parse(due),
        null,
        BigDecimal.TEN,
        true,
        List.of("online_upload"),
        submitted);
  }

  private static StoredChallenge challenge(long id, String name, Deliverable... deliverables) {
    return new StoredChallenge(
        id,
        94063,
        "05.557 - Àlgebra - Aula 2",
        null,
        (int) id,
        name,
        BigDecimal.valueOf(15),
        ChallengeStatus.EXTRACTED,
        "<p>Enunciat</p>",
        null,
        null,
        List.of(deliverables));
  }

  /** The same requirement in two deliverables is listed once. */
  private static ChallengeSpec spec(AiPolicyLevel level, String evidence, String... formats) {
    List<String> requirements = List.of(formats);
    return new ChallengeSpec(
        1,
        "Repte",
        "ca",
        "Resum",
        List.of(
            new ChallengeSpec.Deliverable(
                "assignment:1", DeliverableType.DOCUMENT, null, requirements),
            new ChallengeSpec.Deliverable(
                "assignment:2", DeliverableType.DOCUMENT, null, requirements)),
        List.of(),
        level == null ? null : new AiPolicy(level, List.of(), false, evidence),
        List.of(),
        List.of(),
        List.of(),
        0.9);
  }
}
