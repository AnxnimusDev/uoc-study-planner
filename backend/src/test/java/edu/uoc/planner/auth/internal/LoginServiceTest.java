package edu.uoc.planner.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoginServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
  private static final AuthProperties PROPERTIES =
      new AuthProperties(
          URI.create("https://aula.uoc.edu"), Duration.ofMinutes(10), Duration.ofSeconds(2), false);

  private final SessionStore store = mock(SessionStore.class);

  @Test
  void storesTheCapturedCookiesAsOneCookieHeader() {
    BrowserLogin browser =
        (url, timeout, poll, headless) ->
            List.of(
                new BrowserCookie("canvas_session", "abc"),
                new BrowserCookie("_csrf_token", "xyz"));

    int count = service(browser).login();

    assertThat(count).isEqualTo(2);
    verify(store).replace("canvas_session=abc; _csrf_token=xyz", NOW);
  }

  @Test
  void opensTheConfiguredCanvasUrl() {
    BrowserLogin browser =
        (url, timeout, poll, headless) -> {
          assertThat(url).isEqualTo(PROPERTIES.canvasUrl());
          assertThat(timeout).isEqualTo(PROPERTIES.loginTimeout());
          return List.of(new BrowserCookie("canvas_session", "abc"));
        };

    service(browser).login();
  }

  @Test
  void failsWithoutTouchingTheStoreWhenNoCookieWasCaptured() {
    BrowserLogin browser = (url, timeout, poll, headless) -> List.of();

    assertThatThrownBy(() -> service(browser).login()).isInstanceOf(LoginFailedException.class);
    verify(store, never()).replace(any(), any());
  }

  private LoginService service(BrowserLogin browser) {
    return new LoginService(browser, store, PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
  }
}
