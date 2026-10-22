package edu.uoc.planner.output;

import edu.uoc.planner.planning.Notice;
import edu.uoc.planner.planning.PlannedSession;
import java.util.List;

/** What the two calendar feeds show: deadlines and recording windows, and the study sessions. */
public record CalendarSnapshot(
    List<DeliverableDeadline> deadlines, List<PlannedSession> sessions, List<Notice> notices) {

  public static final CalendarSnapshot EMPTY =
      new CalendarSnapshot(List.of(), List.of(), List.of());

  public CalendarSnapshot {
    deadlines = List.copyOf(deadlines);
    sessions = List.copyOf(sessions);
    notices = List.copyOf(notices);
  }
}
