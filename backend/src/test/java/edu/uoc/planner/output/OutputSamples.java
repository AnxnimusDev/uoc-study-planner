package edu.uoc.planner.output;

import edu.uoc.planner.extraction.ChallengeSpec.AiPolicyLevel;
import edu.uoc.planner.extraction.ChallengeSpec.TaskKind;
import edu.uoc.planner.planning.Notice;
import edu.uoc.planner.planning.PlannedSession;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/** One study week (from Monday 2026-10-26) shared by the output tests. */
final class OutputSamples {

  static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
  static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);

  private OutputSamples() {}

  static Instant at(LocalDate day, String time) {
    return ZonedDateTime.of(day, LocalTime.parse(time), MADRID).toInstant();
  }

  static PlannedSession session(
      String taskId, String title, LocalDate day, String start, String end) {
    return new PlannedSession(
        taskId, 565757, "05.557", title, TaskKind.WORK, at(day, start), at(day, end), false);
  }

  static List<PlannedSession> sessions() {
    return List.of(
        session(
            "565757:t1", "Llegir la guia d'estudi setmanal del Repte 1", MONDAY, "18:15", "19:00"),
        session(
            "565757:t2",
            "Estudiar el material del Repte 1, seguint la GES",
            MONDAY.plusDays(1),
            "18:15",
            "20:15"),
        session(
            "565757:t2",
            "Estudiar el material del Repte 1, seguint la GES",
            MONDAY.plusDays(3),
            "18:15",
            "19:15"));
  }

  static List<DeliverableDeadline> deadlines() {
    return List.of(
        new DeliverableDeadline(
            979698, "QÜESTIONARI 2 - Setmana 3", "05.557", Instant.parse("2026-10-30T22:59:59Z")));
  }

  static List<Notice> notices() {
    return List.of(
        new Notice(
            "565760:t9",
            Notice.Type.VIDEO_WINDOW,
            Instant.parse("2026-11-03T23:00:00Z"),
            Instant.parse("2026-11-08T22:59:59Z"),
            "Graba el vídeo dentro de la ventana."));
  }

  static WeeklyReport report() {
    return new WeeklyReport(
        MONDAY,
        MADRID,
        sessions(),
        deadlines(),
        List.of(
            new WeeklyReport.ChallengeAtRisk(
                "Repte 1. Investigació", "05.590", Duration.ofMinutes(270))),
        notices(),
        List.of(
            new WeeklyReport.ChallengeRules(
                "Repte 1 (Àlgebra)",
                "05.557",
                AiPolicyLevel.FORBIDDEN,
                "En aquesta activitat no està permès l’ús d’eines d’intel·ligència artificial.",
                List.of("Fitxer PDF", "Portada amb nom i assignatura"))));
  }
}
