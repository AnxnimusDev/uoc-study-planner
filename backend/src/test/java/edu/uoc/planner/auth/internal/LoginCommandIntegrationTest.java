package edu.uoc.planner.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import edu.uoc.planner.TestcontainersConfiguration;
import edu.uoc.planner.canvas.CanvasSessionProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/** {@code make login} wiring, with the browser replaced by a canned capture. */
@SpringBootTest(properties = "planner.command=login")
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, LoginCommandIntegrationTest.FakeBrowser.class})
class LoginCommandIntegrationTest {

  @Autowired private LoginCommand command;
  @Autowired private CanvasSessionProvider sessions;

  @Test
  void storesTheSessionCanvasWillUse() {
    assertThat(command.getExitCode()).isZero();
    assertThat(sessions.currentCookie()).hasValue("canvas_session=from-browser");
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FakeBrowser {

    @Bean
    @Primary
    BrowserLogin fakeBrowserLogin() {
      BrowserLogin browser = org.mockito.Mockito.mock(BrowserLogin.class);
      when(browser.capture(any(), any(), any(), anyBoolean()))
          .thenReturn(List.of(new BrowserCookie("canvas_session", "from-browser")));
      return browser;
    }
  }
}
