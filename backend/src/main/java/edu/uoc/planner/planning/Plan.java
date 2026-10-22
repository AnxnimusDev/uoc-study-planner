package edu.uoc.planner.planning;

import java.time.Duration;
import java.util.List;

/**
 * @param sessions sorted by start, including the kept ones
 * @param deferredTaskIds open after the horizon, or due after it and not finished within it:
 *     planned by a later run, not at risk
 * @param unscheduled total missing time of the at-risk tasks ({@code
 *     plan_version.unscheduled_hours})
 */
public record Plan(
    List<PlannedSession> sessions,
    List<AtRiskTask> atRisk,
    List<Notice> notices,
    List<String> deferredTaskIds,
    List<Long> atRiskChallengeIds,
    Duration unscheduled) {

  public Plan {
    sessions = List.copyOf(sessions);
    atRisk = List.copyOf(atRisk);
    notices = List.copyOf(notices);
    deferredTaskIds = List.copyOf(deferredTaskIds);
    atRiskChallengeIds = List.copyOf(atRiskChallengeIds);
  }
}
