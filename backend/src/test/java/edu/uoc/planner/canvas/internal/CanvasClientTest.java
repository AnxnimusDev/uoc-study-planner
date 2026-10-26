package edu.uoc.planner.canvas.internal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import edu.uoc.planner.Fixtures;
import edu.uoc.planner.canvas.CanvasRequestRejectedException;
import edu.uoc.planner.canvas.CanvasSessionExpiredEvent;
import edu.uoc.planner.canvas.CanvasSessionExpiredException;
import edu.uoc.planner.canvas.CanvasUnavailableException;
import edu.uoc.planner.canvas.CanvasUnexpectedResponseException;
import edu.uoc.planner.canvas.internal.api.CanvasAssignment;
import edu.uoc.planner.canvas.internal.api.CanvasCourse;
import edu.uoc.planner.canvas.internal.api.CanvasModule;
import edu.uoc.planner.canvas.internal.api.CanvasModuleItem;
import edu.uoc.planner.canvas.internal.api.CanvasPage;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.WebClient;

class CanvasClientTest {

  @RegisterExtension
  static WireMockExtension canvas =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static final String COOKIE = "canvas_session=test-session";
  private static final String CANVAS_JSON = "application/json; charset=utf-8";
  private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");

  private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
  private final List<Object> events = new ArrayList<>();
  private Optional<String> cookie = Optional.of(COOKIE);
  private CanvasClient client;

  @BeforeEach
  void setUp() {
    client =
        new CanvasClient(
            CanvasWebClient.create(WebClient.builder(), properties()),
            properties(),
            () -> cookie,
            events::add,
            mapper,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void stripsTheWhilePrefixBeforeParsing() {
    stubJson("/api/v1/courses", "while(1);" + Fixtures.json("canvas/courses.json"));

    List<CanvasCourse> courses = client.courses();

    assertThat(courses).hasSize(7);
    assertThat(courses).extracting(CanvasCourse::id).contains(94063L, 92868L, 22184L);
  }

  @Test
  void sendsTheSessionCookieAndCanvasHeaders() {
    stubJson("/api/v1/courses", Fixtures.json("canvas/courses.json"));

    client.courses();

    canvas.verify(
        getRequestedFor(urlPathEqualTo("/api/v1/courses"))
            .withHeader("Cookie", equalTo(COOKIE))
            .withHeader("Accept", equalTo("application/json+canvas-string-ids"))
            .withQueryParam("enrollment_state", equalTo("active"))
            .withQueryParam("per_page", equalTo("100")));
  }

  @Test
  void requestsModulesWithTheirItems() {
    stubJson("/api/v1/courses/94063/modules", Fixtures.json("canvas/algebra-modules.json"));

    List<CanvasModule> modules = client.modules(94063);

    canvas.verify(
        getRequestedFor(urlPathEqualTo("/api/v1/courses/94063/modules"))
            .withQueryParam("include[]", equalTo("items"))
            .withQueryParam("per_page", equalTo("100")));
    assertThat(modules).hasSize(7);
    assertThat(modules.stream().flatMap(m -> m.items().stream()))
        .extracting(CanvasModuleItem::contentId)
        .contains(979687L, 979674L);
  }

  @Test
  void requestsAssignmentsWithTheStudentSubmission() {
    stubJson(
        "/api/v1/courses/94063/assignments",
        Fixtures.json("canvas/algebra-assignments-submissions.json"));

    List<CanvasAssignment> assignments = client.assignments(94063);

    canvas.verify(
        getRequestedFor(urlPathEqualTo("/api/v1/courses/94063/assignments"))
            .withQueryParam("include[]", equalTo("submission"))
            .withQueryParam("per_page", equalTo("100")));
    assertThat(assignments)
        .filteredOn(a -> a.id() == 979698L)
        .singleElement()
        .satisfies(a -> assertThat(a.submission().workflowState()).isEqualTo("graded"));
  }

  @Test
  void readsASinglePage() {
    stubJson(
        "/api/v1/courses/94063/pages/enunciat-de-lactivitat-r1",
        Fixtures.json("canvas/algebra-page-enunciat-r1.json"));

    CanvasPage page = client.page(94063, "enunciat-de-lactivitat-r1");

    assertThat(page.url()).isEqualTo("enunciat-de-lactivitat-r1");
    assertThat(page.body()).isNotBlank();
  }

  @Test
  void readsASingleAssignment() throws Exception {
    JsonNode quiz = mapper.readTree(Fixtures.json("canvas/algebra-assignments.json")).get(7);
    stubJson("/api/v1/courses/94063/assignments/" + quiz.get("id").asLong(), quiz.toString());

    CanvasAssignment assignment = client.assignment(94063, quiz.get("id").asLong());

    assertThat(assignment.id()).isEqualTo(quiz.get("id").asLong());
    assertThat(assignment.name()).isEqualTo(quiz.get("name").asText());
  }

  @Test
  void followsNextLinksUntilTheLastPage() throws Exception {
    ArrayNode all = (ArrayNode) mapper.readTree(Fixtures.json("canvas/algebra-assignments.json"));
    String path = "/api/v1/courses/94063/assignments";
    String secondPage = canvas.baseUrl() + path + "?page=2&per_page=100";
    canvas.stubFor(
        get(urlPathEqualTo(path))
            .withQueryParam("page", equalTo("2"))
            .willReturn(okForContentType(CANVAS_JSON, slice(all, 10, all.size()).toString())));
    canvas.stubFor(
        get(urlPathEqualTo(path))
            .withQueryParam("page", absent())
            .willReturn(
                okForContentType(CANVAS_JSON, slice(all, 0, 10).toString())
                    .withHeader(
                        "Link",
                        "<"
                            + canvas.baseUrl()
                            + path
                            + "?page=1&per_page=100>; rel=\"current\","
                            + "<"
                            + secondPage
                            + ">; rel=\"next\","
                            + "<"
                            + secondPage
                            + ">; rel=\"last\"")));

    List<CanvasAssignment> assignments = client.assignments(94063);

    assertThat(assignments).hasSize(20);
    canvas.verify(2, getRequestedFor(urlPathEqualTo(path)));
  }

  @Test
  void refusesToFollowANextLinkToAnotherHost() {
    stubJsonWithLink(
        "/api/v1/courses/94063/assignments",
        Fixtures.json("canvas/algebra-assignments.json"),
        "<https://evil.example/api/v1/courses/94063/assignments?page=2>; rel=\"next\"");

    assertThatThrownBy(() -> client.assignments(94063))
        .isInstanceOf(CanvasRequestRejectedException.class);
    canvas.verify(1, anyRequestedFor(anyUrl()));
  }

  @Test
  void expiresTheSessionOn401WithoutRetrying() {
    canvas.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(401)));

    assertThatThrownBy(() -> client.courses()).isInstanceOf(CanvasSessionExpiredException.class);

    canvas.verify(1, anyRequestedFor(anyUrl()));
    assertThat(events).containsExactly(new CanvasSessionExpiredEvent(NOW));
  }

  @Test
  void failsWithoutCallingCanvasWhenThereIsNoSession() {
    cookie = Optional.empty();

    assertThatThrownBy(() -> client.courses()).isInstanceOf(CanvasSessionExpiredException.class);

    canvas.verify(0, anyRequestedFor(anyUrl()));
    assertThat(events).isEmpty();
  }

  @Test
  void retriesWhenRateLimited() {
    canvas.stubFor(
        get(urlPathEqualTo("/api/v1/courses"))
            .inScenario("rate limit")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(429))
            .willSetStateTo("recovered"));
    canvas.stubFor(
        get(urlPathEqualTo("/api/v1/courses"))
            .inScenario("rate limit")
            .whenScenarioStateIs("recovered")
            .willReturn(okForContentType(CANVAS_JSON, Fixtures.json("canvas/courses.json"))));

    assertThat(client.courses()).hasSize(7);
    canvas.verify(2, getRequestedFor(urlPathEqualTo("/api/v1/courses")));
  }

  @Test
  void givesUpAfterTheConfiguredAttemptsOnServerErrors() {
    canvas.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(503)));

    assertThatThrownBy(() -> client.courses()).isInstanceOf(CanvasUnavailableException.class);

    canvas.verify(properties().maxAttempts(), anyRequestedFor(anyUrl()));
  }

  @Test
  void doesNotRetryOtherClientErrors() {
    canvas.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(404)));

    assertThatThrownBy(() -> client.page(94063, "missing"))
        .isInstanceOf(CanvasUnexpectedResponseException.class);

    canvas.verify(1, anyRequestedFor(anyUrl()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
  void rejectsAnyMethodOtherThanGetBeforeItLeaves(String method) {
    WebClient webClient = CanvasWebClient.create(WebClient.builder(), properties());

    assertThatThrownBy(
            () ->
                webClient
                    .method(HttpMethod.valueOf(method))
                    .uri(URI.create(canvas.baseUrl() + "/api/v1/courses/94063/assignments"))
                    .retrieve()
                    .toBodilessEntity()
                    .block())
        .isInstanceOf(CanvasRequestRejectedException.class);

    canvas.verify(0, anyRequestedFor(anyUrl()));
  }

  private CanvasProperties properties() {
    return new CanvasProperties(
        URI.create(canvas.baseUrl()),
        100,
        3,
        Duration.ofMillis(10),
        Duration.ofSeconds(5),
        DataSize.ofMegabytes(10));
  }

  private void stubJson(String path, String body) {
    canvas.stubFor(get(urlPathEqualTo(path)).willReturn(okForContentType(CANVAS_JSON, body)));
  }

  private void stubJsonWithLink(String path, String body, String link) {
    canvas.stubFor(
        get(urlPathEqualTo(path))
            .willReturn(okForContentType(CANVAS_JSON, body).withHeader("Link", link)));
  }

  private ArrayNode slice(ArrayNode all, int from, int to) {
    ArrayNode part = mapper.createArrayNode();
    for (int i = from; i < to; i++) {
      JsonNode element = all.get(i);
      part.add(element);
    }
    return part;
  }
}
