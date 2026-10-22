package edu.uoc.planner.planning;

import edu.uoc.planner.availability.FreeSlot;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * @param zone the student's, for days (daily limit, courses per day) and margins
 * @param horizonWeeks how far ahead to plan (6 in the design)
 * @param keptSessions from the previous plan: done or in the next two days; they keep their time
 */
public record PlanRequest(
    Instant now,
    ZoneId zone,
    List<PlanTask> tasks,
    List<FreeSlot> slots,
    PlanLimits limits,
    int horizonWeeks,
    List<PlannedSession> keptSessions) {

  public PlanRequest {
    tasks = List.copyOf(tasks);
    slots = List.copyOf(slots);
    keptSessions = List.copyOf(keptSessions);
  }
}
