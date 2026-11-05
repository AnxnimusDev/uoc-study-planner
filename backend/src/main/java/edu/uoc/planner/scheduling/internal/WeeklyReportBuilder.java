package edu.uoc.planner.scheduling.internal;

import edu.uoc.planner.canvas.Deliverable;
import edu.uoc.planner.canvas.StoredChallenge;
import edu.uoc.planner.extraction.ChallengeSpec;
import edu.uoc.planner.output.DeliverableDeadline;
import edu.uoc.planner.output.WeeklyReport;
import edu.uoc.planner.output.WeeklyReport.ChallengeAtRisk;
import edu.uoc.planner.output.WeeklyReport.ChallengeRules;
import edu.uoc.planner.planning.AtRiskTask;
import edu.uoc.planner.planning.Plan;
import edu.uoc.planner.planning.PlannedSession;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The seven days from today, as the weekly PDF shows them. */
final class WeeklyReportBuilder {

  private WeeklyReportBuilder() {}

  /**
   * @param specs valid spec by challenge id; a challenge without one has no rules to show
   */
  static WeeklyReport build(
      Plan plan,
      ZoneId zone,
      List<StoredChallenge> challenges,
      Map<Long, ChallengeSpec> specs,
      Instant now) {
    LocalDate today = now.atZone(zone).toLocalDate();
    Instant end = today.plusDays(7).atStartOfDay(zone).toInstant();
    List<PlannedSession> sessions =
        plan.sessions().stream().filter(s -> s.start().isBefore(end)).toList();

    Set<Long> thisWeek = new HashSet<>();
    sessions.forEach(s -> thisWeek.add(s.challengeId()));
    List<DeliverableDeadline> deadlines = new ArrayList<>();
    for (StoredChallenge challenge : challenges) {
      for (Deliverable d : challenge.deliverables()) {
        if (!d.submitted()
            && d.dueAt() != null
            && d.dueAt().isAfter(now)
            && d.dueAt().isBefore(end)) {
          deadlines.add(
              new DeliverableDeadline(
                  d.canvasAssignmentId(), d.name(), challenge.courseShortCode(), d.dueAt()));
          thisWeek.add(challenge.id());
        }
      }
    }
    List<ChallengeRules> rules =
        challenges.stream()
            .filter(c -> thisWeek.contains(c.id()) && specs.containsKey(c.id()))
            .map(c -> rules(c, specs.get(c.id())))
            .toList();
    return new WeeklyReport(
        today, zone, sessions, deadlines, risks(plan, challenges), plan.notices(), rules);
  }

  /** Weekly study time (ADR 0007) belongs to no challenge, so it is not among them. */
  private static List<ChallengeAtRisk> risks(Plan plan, List<StoredChallenge> challenges) {
    Map<Long, StoredChallenge> byId = new HashMap<>();
    challenges.forEach(c -> byId.put(c.id(), c));
    Map<Long, Duration> missing = new LinkedHashMap<>();
    for (AtRiskTask risk : plan.atRisk()) {
      if (byId.containsKey(risk.challengeId())) {
        missing.merge(risk.challengeId(), risk.missing(), Duration::plus);
      }
    }
    return missing.entrySet().stream()
        .map(
            e -> {
              StoredChallenge c = byId.get(e.getKey());
              return new ChallengeAtRisk(c.name(), c.courseShortCode(), e.getValue());
            })
        .toList();
  }

  private static ChallengeRules rules(StoredChallenge challenge, ChallengeSpec spec) {
    List<String> formats =
        spec.deliverables().stream()
            .flatMap(d -> d.formatRequirements().stream())
            .distinct()
            .toList();
    ChallengeSpec.AiPolicy policy = spec.aiPolicy();
    return new ChallengeRules(
        challenge.name(),
        challenge.courseShortCode(),
        policy == null ? null : policy.level(),
        policy == null ? null : policy.evidence(),
        formats);
  }
}
