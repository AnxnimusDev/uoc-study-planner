package edu.uoc.planner.output;

import edu.uoc.planner.planning.Notice;
import edu.uoc.planner.planning.PlannedSession;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Action;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.XProperty;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;
import net.fortuna.ical4j.validate.ValidationException;
import org.springframework.stereotype.Component;

/**
 * The two subscription calendars (docs/design.md, section 9). UIDs are stable so a calendar app
 * updates events in place: {@code deliverable-<id>}, {@code notice-<task>} and {@code
 * session-<task>-<n>}, where {@code n} counts the sessions of a task in time order.
 */
@Component
public final class IcsCalendars {

  private static final String DOMAIN = "@uoc-study-planner";
  private static final Duration DEADLINE_BLOCK = Duration.ofMinutes(30);

  private final Clock clock;

  public IcsCalendars(Clock clock) {
    this.clock = clock;
  }

  /** {@code UOC · Entregas}: deadlines (with a reminder one day before) and recording windows. */
  public String deliverables(CalendarSnapshot snapshot) {
    Calendar calendar = calendar("UOC · Entregas");
    snapshot.deadlines().stream()
        .sorted(Comparator.comparing(DeliverableDeadline::dueAt))
        .forEach(
            d -> {
              VEvent event =
                  event(
                      "deliverable-" + d.canvasAssignmentId(),
                      d.dueAt().minus(DEADLINE_BLOCK),
                      d.dueAt(),
                      "Entrega: " + d.title() + " (" + d.courseCode() + ")",
                      "Hora límite de entrega en el aula.");
              VAlarm alarm = new VAlarm(Duration.ofDays(-1));
              alarm.add(new Action("DISPLAY"));
              alarm.add(new Description("Mañana vence: " + d.title()));
              event.add(alarm);
              calendar.add(event);
            });
    snapshot.notices().stream()
        .filter(n -> n.type() == Notice.Type.VIDEO_WINDOW && n.to() != null)
        .forEach(
            n ->
                calendar.add(
                    event(
                        "notice-" + sanitize(n.taskId()),
                        n.from(),
                        n.to(),
                        "Ventana de grabación del vídeo de autoría",
                        n.message())));
    return write(calendar);
  }

  /** {@code UOC · Plan de estudio}: one event per planned session. */
  public String plan(CalendarSnapshot snapshot) {
    Calendar calendar = calendar("UOC · Plan de estudio");
    Map<String, Integer> perTask = new HashMap<>();
    snapshot.sessions().stream()
        .sorted(Comparator.comparing(PlannedSession::start).thenComparing(PlannedSession::taskId))
        .forEach(
            s -> {
              int n = perTask.merge(s.taskId(), 1, Integer::sum);
              calendar.add(
                  event(
                      "session-" + sanitize(s.taskId()) + "-" + n,
                      s.start(),
                      s.end(),
                      s.title() + " (" + s.courseCode() + ")",
                      "Sesión de estudio · " + s.kind()));
            });
    return write(calendar);
  }

  private Calendar calendar(String name) {
    Calendar calendar = new Calendar();
    calendar.add(new ProdId("-//UOC Study Planner//ES"));
    calendar.add(ImmutableVersion.VERSION_2_0);
    calendar.add(new CalScale(CalScale.VALUE_GREGORIAN));
    calendar.add(new XProperty("X-WR-CALNAME", name));
    // Hint for calendar apps: refresh the subscription every hour.
    calendar.add(new XProperty("X-PUBLISHED-TTL", "PT1H"));
    return calendar;
  }

  private VEvent event(String id, Instant start, Instant end, String summary, String description) {
    VEvent event = new VEvent(false);
    event.add(new Uid(id + DOMAIN));
    event.add(new DtStamp(clock.instant()));
    event.add(new DtStart<>(start));
    event.add(new DtEnd<>(end));
    event.add(new Summary(summary));
    event.add(new Description(description));
    return event;
  }

  private static String write(Calendar calendar) {
    StringWriter out = new StringWriter();
    // ical4j's validator rejects a calendar without components, which is still a valid feed.
    try {
      new CalendarOutputter(!calendar.getComponents().isEmpty()).output(calendar, out);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (ValidationException e) {
      throw new IllegalStateException("Invalid calendar: " + e.getMessage(), e);
    }
    return out.toString();
  }

  /** Task ids such as {@code review:1:assignment:2} → {@code review-1-assignment-2}. */
  private static String sanitize(String id) {
    return id.replaceAll("[^A-Za-z0-9.-]", "-");
  }
}
