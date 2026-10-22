package edu.uoc.planner.output.internal;

import edu.uoc.planner.output.CalendarSnapshot;
import edu.uoc.planner.output.CalendarSource;
import edu.uoc.planner.output.IcsCalendars;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two subscription feeds. A wrong token gets a 404, so the feeds cannot be told apart from a
 * missing page.
 */
/** Registered by {@link OutputConfiguration}: its calendar source is optional. */
@RestController
class FeedController {

  static final MediaType CALENDAR =
      new MediaType("text", "calendar", java.nio.charset.StandardCharsets.UTF_8);

  private final FeedTokenStore tokens;
  private final Supplier<CalendarSource> source;
  private final IcsCalendars calendars;

  FeedController(FeedTokenStore tokens, Supplier<CalendarSource> source, IcsCalendars calendars) {
    this.tokens = tokens;
    this.source = source;
    this.calendars = calendars;
  }

  @GetMapping("/calendar/{token}/entregas.ics")
  ResponseEntity<String> deliverables(@PathVariable String token) {
    return feed(token, calendars::deliverables);
  }

  @GetMapping("/calendar/{token}/plan.ics")
  ResponseEntity<String> plan(@PathVariable String token) {
    return feed(token, calendars::plan);
  }

  private ResponseEntity<String> feed(String token, Function<CalendarSnapshot, String> render) {
    if (!tokens.matches(token)) {
      return ResponseEntity.notFound().build();
    }
    CalendarSource current = source.get();
    CalendarSnapshot snapshot = current == null ? CalendarSnapshot.EMPTY : current.current();
    return ResponseEntity.ok().contentType(CALENDAR).body(render.apply(snapshot));
  }
}
