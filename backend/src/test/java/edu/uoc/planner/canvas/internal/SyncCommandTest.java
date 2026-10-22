package edu.uoc.planner.canvas.internal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import edu.uoc.planner.Fixtures;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.WebClient;

class SyncCommandTest {

  @RegisterExtension
  static WireMockExtension canvas =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static final String JSON = "application/json; charset=utf-8";

  private final ByteArrayOutputStream output = new ByteArrayOutputStream();
  private Optional<String> cookie = Optional.of("canvas_session=abc");

  @Test
  void listsTheCoursesOfTheCurrentTerm() {
    canvas.stubFor(
        get(urlPathEqualTo("/api/v1/users/self"))
            .willReturn(okForContentType(JSON, Fixtures.json("canvas/users-self.json"))));
    canvas.stubFor(
        get(urlPathEqualTo("/api/v1/courses"))
            .willReturn(okForContentType(JSON, Fixtures.json("canvas/courses.json"))));

    SyncCommand command = command();
    command.run(null);

    assertThat(command.getExitCode()).isZero();
    assertThat(output())
        .contains("05.557 - Àlgebra - Aula 2", "05.590 - Interacció persona ordinador - Aula 2")
        .contains("6 asignaturas")
        .doesNotContain("Tutoria");
  }

  @Test
  void asksToLogInWhenThereIsNoSession() {
    cookie = Optional.empty();

    SyncCommand command = command();
    command.run(null);

    assertThat(command.getExitCode()).isEqualTo(1);
    assertThat(output()).contains("make login");
  }

  @Test
  void asksToLogInWhenTheSessionExpired() {
    canvas.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(401)));

    SyncCommand command = command();
    command.run(null);

    assertThat(command.getExitCode()).isEqualTo(1);
    assertThat(output()).contains("make login");
  }

  private SyncCommand command() {
    CanvasProperties properties =
        new CanvasProperties(
            URI.create(canvas.baseUrl()),
            100,
            1,
            Duration.ofMillis(10),
            Duration.ofSeconds(5),
            DataSize.ofMegabytes(10));
    CanvasClient client =
        new CanvasClient(
            CanvasWebClient.create(WebClient.builder(), properties),
            properties,
            () -> cookie,
            event -> {},
            Jackson2ObjectMapperBuilder.json().build(),
            Clock.systemUTC());
    return new SyncCommand(client, new PrintStream(output, true, StandardCharsets.UTF_8));
  }

  private String output() {
    return output.toString(StandardCharsets.UTF_8);
  }
}
