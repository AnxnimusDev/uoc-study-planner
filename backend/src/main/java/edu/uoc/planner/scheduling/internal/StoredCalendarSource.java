package edu.uoc.planner.scheduling.internal;

import edu.uoc.planner.canvas.CanvasCatalog;
import edu.uoc.planner.canvas.StoredChallenge;
import edu.uoc.planner.output.CalendarSnapshot;
import edu.uoc.planner.output.CalendarSource;
import edu.uoc.planner.output.DeliverableDeadline;
import edu.uoc.planner.planning.PlanStore;
import java.util.List;
import org.springframework.stereotype.Component;

/** The calendar feeds read the stored plan and the deadlines of the selected courses. */
@Component
final class StoredCalendarSource implements CalendarSource {

  private final CanvasCatalog catalog;
  private final PlanStore plans;

  StoredCalendarSource(CanvasCatalog catalog, PlanStore plans) {
    this.catalog = catalog;
    this.plans = plans;
  }

  @Override
  public CalendarSnapshot current() {
    List<DeliverableDeadline> deadlines =
        catalog.selectedChallenges().stream()
            .flatMap(
                (StoredChallenge c) ->
                    c.deliverables().stream()
                        .filter(d -> d.dueAt() != null)
                        .map(
                            d ->
                                new DeliverableDeadline(
                                    d.canvasAssignmentId(),
                                    d.name(),
                                    c.courseShortCode(),
                                    d.dueAt())))
            .toList();
    return new CalendarSnapshot(deadlines, plans.latestSessions(), plans.latestNotices());
  }
}
