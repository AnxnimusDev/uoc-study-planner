package edu.uoc.planner.scheduling;

import edu.uoc.planner.planning.Plan;
import java.time.ZoneId;
import java.util.List;

/**
 * @param unestimated deliverables left out of the plan for lack of an estimate
 * @param unknownWeeklyCourses {@code weeklyStudy} codes that are not a selected course
 * @param zone the student's, to show the plan
 */
public record PlanReport(
    Plan plan, List<String> unestimated, List<String> unknownWeeklyCourses, ZoneId zone) {

  public PlanReport {
    unestimated = List.copyOf(unestimated);
    unknownWeeklyCourses = List.copyOf(unknownWeeklyCourses);
  }
}
