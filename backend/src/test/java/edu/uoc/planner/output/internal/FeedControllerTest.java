package edu.uoc.planner.output.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.uoc.planner.output.CalendarSnapshot;
import edu.uoc.planner.output.CalendarSource;
import edu.uoc.planner.output.IcsCalendars;
import java.time.Clock;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FeedControllerTest {

  private static final String TOKEN = "c2VjcmV0LXRva2VuLWZvci10ZXN0cy0wMDAwMDAwMDA";

  private final FeedTokenStore tokens = mock(FeedTokenStore.class);

  @Test
  void servesBothCalendarsWithTheRightToken() throws Exception {
    when(tokens.matches(TOKEN)).thenReturn(true);
    MockMvc mvc = mvc(Optional.empty());

    mvc.perform(get("/calendar/" + TOKEN + "/entregas.ics"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/calendar"))
        .andExpect(content().string(Matchers.containsString("UOC · Entregas")));
    mvc.perform(get("/calendar/" + TOKEN + "/plan.ics"))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("UOC · Plan de estudio")));
  }

  @Test
  void hidesTheFeedBehindA404ForAWrongToken() throws Exception {
    mvc(Optional.empty()).perform(get("/calendar/wrong/plan.ics")).andExpect(status().isNotFound());
  }

  @Test
  void usesTheCalendarSourceWhenThereIsOne() throws Exception {
    when(tokens.matches(TOKEN)).thenReturn(true);
    CalendarSource source =
        () ->
            new CalendarSnapshot(
                java.util.List.of(),
                edu.uoc.planner.output.OutputSamplesAccess.sessions(),
                java.util.List.of());

    mvc(Optional.of(source))
        .perform(get("/calendar/" + TOKEN + "/plan.ics"))
        .andExpect(
            content().string(Matchers.containsString("session-565757-t1-1@uoc-study-planner")));
  }

  private MockMvc mvc(Optional<CalendarSource> source) {
    FeedController controller =
        new FeedController(tokens, () -> source.orElse(null), new IcsCalendars(Clock.systemUTC()));
    return MockMvcBuilders.standaloneSetup(controller).build();
  }
}
