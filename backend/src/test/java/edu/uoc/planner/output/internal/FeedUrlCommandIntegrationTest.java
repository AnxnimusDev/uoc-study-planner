package edu.uoc.planner.output.internal;

import static org.assertj.core.api.Assertions.assertThat;

import edu.uoc.planner.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** {@code make feed}: prints the two subscription URLs at startup. */
@SpringBootTest(properties = "planner.command=feed-url")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FeedUrlCommandIntegrationTest {

  @Autowired private FeedUrlCommand command;
  @Autowired private FeedTokenStore tokens;

  @Test
  void printsBothFeedUrls() {
    assertThat(command.getExitCode()).isZero();
    assertThat(command.urls())
        .containsExactly(
            "http://127.0.0.1:8080/calendar/" + tokens.token() + "/entregas.ics",
            "http://127.0.0.1:8080/calendar/" + tokens.token() + "/plan.ics");
  }
}
