package edu.uoc.planner.auth.internal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import edu.uoc.planner.Fixtures;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Drives a real headless Chromium against WireMock. Needs the Playwright browsers, so it is left
 * out of {@code mvn verify}: {@code ./mvnw test -Dgroups=browser -Dsurefire.excludedGroups=}.
 */
@Tag("browser")
class PlaywrightBrowserLoginTest {

  @RegisterExtension
  static WireMockExtension canvas =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private final PlaywrightBrowserLogin login = new PlaywrightBrowserLogin();

  @Test
  void capturesTheCookiesOnceUsersSelfAnswers200() {
    canvas.stubFor(
        get(urlEqualTo("/"))
            .willReturn(
                okForContentType("text/html", "<html><body>Aula</body></html>")
                    .withHeader("Set-Cookie", "canvas_session=abc123; Path=/; HttpOnly")));
    canvas.stubFor(
        get(urlEqualTo("/api/v1/users/self"))
            .atPriority(1)
            .withHeader("Cookie", containing("canvas_session=abc123"))
            .willReturn(
                okForContentType(
                    "application/json; charset=utf-8",
                    "while(1);" + Fixtures.json("canvas/users-self.json"))));
    canvas.stubFor(
        get(urlEqualTo("/api/v1/users/self"))
            .atPriority(2)
            .willReturn(aResponse().withStatus(401)));

    List<BrowserCookie> cookies =
        login.capture(
            URI.create(canvas.baseUrl()), Duration.ofSeconds(20), Duration.ofMillis(200), true);

    assertThat(cookies).containsExactly(new BrowserCookie("canvas_session", "abc123"));
  }

  @Test
  void ignoresAnHtmlLoginPageServedWith200() {
    canvas.stubFor(get(urlEqualTo("/")).willReturn(okForContentType("text/html", "<html></html>")));
    canvas.stubFor(
        get(urlEqualTo("/api/v1/users/self"))
            .willReturn(okForContentType("text/html", "<html>Login SSO</html>")));

    assertThatThrownBy(
            () ->
                login.capture(
                    URI.create(canvas.baseUrl()),
                    Duration.ofSeconds(2),
                    Duration.ofMillis(200),
                    true))
        .isInstanceOf(LoginFailedException.class);
  }
}
