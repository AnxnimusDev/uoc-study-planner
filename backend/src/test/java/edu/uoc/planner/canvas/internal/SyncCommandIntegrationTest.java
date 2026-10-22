package edu.uoc.planner.canvas.internal;

import static org.assertj.core.api.Assertions.assertThat;

import edu.uoc.planner.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** {@code make sync} wiring: the command runs at startup, here without a stored session. */
@SpringBootTest(properties = "planner.command=sync")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SyncCommandIntegrationTest {

  @Autowired private SyncCommand command;

  @Test
  void runsAtStartupAndAsksToLogInWithoutASession() {
    assertThat(command.getExitCode()).isEqualTo(1);
  }
}
