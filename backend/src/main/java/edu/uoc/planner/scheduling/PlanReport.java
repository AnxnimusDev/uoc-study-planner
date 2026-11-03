package edu.uoc.planner.scheduling;

import edu.uoc.planner.planning.Plan;
import java.time.ZoneId;
import java.util.List;

/**
 * @param unestimated deliverables left out of the plan for lack of an estimate
 * @param unknownWeeklyCourses {@code weeklyStudy} codes that are not a selected course
 * @param zone the student's, to show the plan
 * @param progressReset challenges extracted again, whose reported progress was dropped (ADR 0011),
 *     "05.562 · Repte 2"
 */
public record PlanReport(
    Plan plan,
    List<String> unestimated,
    List<String> unknownWeeklyCourses,
    ZoneId zone,
    List<String> progressReset) {

  public PlanReport {
    unestimated = List.copyOf(unestimated);
    unknownWeeklyCourses = List.copyOf(unknownWeeklyCourses);
    progressReset = List.copyOf(progressReset);
  }

  public PlanReport(
      Plan plan, List<String> unestimated, List<String> unknownWeeklyCourses, ZoneId zone) {
    this(plan, unestimated, unknownWeeklyCourses, zone, List.of());
  }
}
