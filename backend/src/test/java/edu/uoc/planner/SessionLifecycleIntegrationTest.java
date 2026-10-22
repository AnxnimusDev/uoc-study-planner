package edu.uoc.planner;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import edu.uoc.planner.canvas.CanvasSessionExpiredException;
import edu.uoc.planner.canvas.CanvasSessionProvider;
import edu.uoc.planner.canvas.internal.CanvasClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** ADR 0005 end to end: auth supplies the stored cookie and learns about the 401 from canvas. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SessionLifecycleIntegrationTest {

  @RegisterExtension
  static WireMockExtension canvas =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void canvasUrl(DynamicPropertyRegistry registry) {
    registry.add("planner.canvas.base-url", canvas::baseUrl);
    registry.add("planner.canvas.initial-backoff", () -> "10ms");
  }

  @Autowired private CanvasClient client;
  @Autowired private CanvasSessionProvider sessions;
  @Autowired private edu.uoc.planner.auth.internal.SessionStore store;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void sendsTheStoredCookieAndExpiresItOn401() {
    store.replace("canvas_session=from-login", java.time.Instant.parse("2026-10-22T08:00:00Z"));
    canvas.stubFor(
        get(urlPathEqualTo("/api/v1/courses"))
            .willReturn(
                okForContentType("application/json", Fixtures.json("canvas/courses.json"))));

    assertThat(client.courses()).hasSize(7);
    canvas.verify(
        getRequestedFor(urlPathEqualTo("/api/v1/courses"))
            .withHeader("Cookie", equalTo("canvas_session=from-login")));

    canvas.stubFor(
        get(urlPathEqualTo("/api/v1/users/self")).willReturn(aResponse().withStatus(401)));

    assertThatThrownBy(() -> client.self()).isInstanceOf(CanvasSessionExpiredException.class);
    assertThat(sessions.currentCookie()).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM session_secret WHERE expired_at IS NOT NULL", Integer.class))
        .isOne();
  }
}
