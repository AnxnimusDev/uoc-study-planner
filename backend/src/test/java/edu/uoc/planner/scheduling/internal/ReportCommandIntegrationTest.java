package edu.uoc.planner.scheduling.internal;

import static org.assertj.core.api.Assertions.assertThat;

import edu.uoc.planner.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** {@code make report} without SMTP: stops at once, before touching Canvas or the plan. */
@SpringBootTest(
    properties = {
      "planner.command=report",
      "spring.mail.host=",
      "planner.output.mail-to=alumne@example.com"
    })
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ReportCommandIntegrationTest {

  @Autowired private ReportCommand command;

  @Test
  void stopsWithoutAnSmtpServer() {
    assertThat(command.getExitCode()).isEqualTo(1);
  }
}
