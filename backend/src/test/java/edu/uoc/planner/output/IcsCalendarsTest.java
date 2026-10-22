package edu.uoc.planner.output;

import static edu.uoc.planner.output.OutputSamples.MONDAY;
import static edu.uoc.planner.output.OutputSamples.session;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import org.junit.jupiter.api.Test;

class IcsCalendarsTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC);

  private final IcsCalendars calendars = new IcsCalendars(CLOCK);

  @Test
  void deliverablesCalendarHasOneEventPerDeadline() throws Exception {
    String ics = calendars.deliverables(snapshot());

    assertThat(ics).contains("X-WR-CALNAME:UOC · Entregas");
    List<VEvent> events = events(ics);
    VEvent quiz = byUid(events, "deliverable-979698@uoc-study-planner");
    assertThat(quiz.getSummary().getValue())
        .isEqualTo("Entrega: QÜESTIONARI 2 - Setmana 3 (05.557)");
    assertThat(quiz.getEndDate().orElseThrow().getValue()).isEqualTo("20261030T225959Z");
    assertThat(quiz.getAlarms()).hasSize(1);
  }

  @Test
  void deliverablesCalendarShowsTheRecordingWindows() throws Exception {
    VEvent window =
        byUid(events(calendars.deliverables(snapshot())), "notice-565760-t9@uoc-study-planner");

    assertThat(window.getSummary().getValue()).startsWith("Ventana de grabación");
    assertThat(window.getStartDate().orElseThrow().getValue()).isEqualTo("20261103T230000Z");
  }

  @Test
  void planCalendarHasOneEventPerSession() throws Exception {
    String ics = calendars.plan(snapshot());

    assertThat(ics).contains("X-WR-CALNAME:UOC · Plan de estudio");
    assertThat(events(ics))
        .extracting(e -> e.getUid().orElseThrow().getValue())
        .containsExactly(
            "session-565757-t1-1@uoc-study-planner",
            "session-565757-t2-1@uoc-study-planner",
            "session-565757-t2-2@uoc-study-planner");
  }

  @Test
  void keepsTheUidWhenASessionMoves() throws Exception {
    CalendarSnapshot moved =
        new CalendarSnapshot(
            List.of(),
            List.of(session("565757:t1", "Llegir", MONDAY.plusDays(2), "10:00", "11:00")),
            List.of());

    VEvent event = events(calendars.plan(moved)).getFirst();

    assertThat(event.getUid().orElseThrow().getValue())
        .isEqualTo("session-565757-t1-1@uoc-study-planner");
    assertThat(event.getStartDate().orElseThrow().getValue()).isEqualTo("20261028T090000Z");
  }

  @Test
  void isDeterministic() {
    assertThat(calendars.plan(snapshot())).isEqualTo(calendars.plan(snapshot()));
  }

  @Test
  void anEmptySnapshotIsStillAValidCalendar() throws Exception {
    String ics = calendars.plan(CalendarSnapshot.EMPTY);

    assertThat(new CalendarBuilder().build(new StringReader(ics)).getComponents()).isEmpty();
  }

  private static CalendarSnapshot snapshot() {
    return new CalendarSnapshot(
        OutputSamples.deadlines(), OutputSamples.sessions(), OutputSamples.notices());
  }

  private static List<VEvent> events(String ics) throws Exception {
    Calendar calendar = new CalendarBuilder().build(new StringReader(ics));
    return calendar.getComponents(VEvent.VEVENT);
  }

  private static VEvent byUid(List<VEvent> events, String uid) {
    return events.stream()
        .filter(e -> e.getUid().orElseThrow().getValue().equals(uid))
        .findFirst()
        .orElseThrow();
  }
}
