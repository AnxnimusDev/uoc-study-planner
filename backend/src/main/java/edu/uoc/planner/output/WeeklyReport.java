package edu.uoc.planner.output;

import edu.uoc.planner.extraction.ChallengeSpec.AiPolicyLevel;
import edu.uoc.planner.planning.Notice;
import edu.uoc.planner.planning.PlannedSession;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Content of the weekly PDF (docs/design.md, section 9): 7 days, deadlines, challenges at risk,
 * notices, and the AI policy and format checklist of each challenge (ADR 0003).
 *
 * @param weekStart first of the seven days
 * @param courseNames course name by code ("05.557" → "Àlgebra"); the code is shown when missing
 */
public record WeeklyReport(
    LocalDate weekStart,
    ZoneId zone,
    List<PlannedSession> sessions,
    List<DeliverableDeadline> deadlines,
    List<ChallengeAtRisk> atRisk,
    List<Notice> notices,
    List<ChallengeRules> rules,
    Map<String, String> courseNames) {

  public WeeklyReport {
    sessions = List.copyOf(sessions);
    deadlines = List.copyOf(deadlines);
    atRisk = List.copyOf(atRisk);
    notices = List.copyOf(notices);
    rules = List.copyOf(rules);
    courseNames = Map.copyOf(courseNames);
  }

  public WeeklyReport(
      LocalDate weekStart,
      ZoneId zone,
      List<PlannedSession> sessions,
      List<DeliverableDeadline> deadlines,
      List<ChallengeAtRisk> atRisk,
      List<Notice> notices,
      List<ChallengeRules> rules) {
    this(weekStart, zone, sessions, deadlines, atRisk, notices, rules, Map.of());
  }

  /** "Àlgebra" for "05.557"; the code itself when its name is unknown. */
  public String courseName(String code) {
    return courseNames.getOrDefault(code, code);
  }

  public record ChallengeAtRisk(String title, String courseCode, Duration missing) {}

  /**
   * @param evidence literal sentence of the statement that sets the AI policy
   */
  public record ChallengeRules(
      String title,
      String courseCode,
      AiPolicyLevel aiPolicy,
      String evidence,
      List<String> formatRequirements) {

    public ChallengeRules {
      formatRequirements = List.copyOf(formatRequirements);
    }
  }
}
