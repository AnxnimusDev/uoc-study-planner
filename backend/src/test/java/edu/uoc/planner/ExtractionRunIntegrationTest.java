package edu.uoc.planner;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import edu.uoc.planner.scheduling.ExtractionReport;
import edu.uoc.planner.scheduling.ExtractionReport.Outcome;
import edu.uoc.planner.scheduling.ExtractionRun;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Stored challenges → Claude (WireMock, synthetic answers) → challenge_spec and status. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ExtractionRunIntegrationTest {

  private static final long ALGEBRA = 94063;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @RegisterExtension
  static WireMockExtension anthropic =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void claude(DynamicPropertyRegistry registry) {
    registry.add("planner.extraction.base-url", anthropic::baseUrl);
    registry.add("planner.extraction.api-key", () -> "test-key");
    registry.add("planner.extraction.max-retries", () -> "0");
  }

  @Autowired private ExtractionRun run;
  @Autowired private JdbcTemplate jdbc;

  private long r1;

  @BeforeEach
  void setUp() throws Exception {
    jdbc.execute(
        "TRUNCATE canvas_page, deliverable, challenge_spec, study_session, task, challenge, course"
            + " RESTART IDENTITY CASCADE");
    anthropic.resetAll();
    jdbc.update(
        "INSERT INTO course (canvas_id, code, name, term_id, selected) VALUES (?, '05.557 - Àlgebra - Aula 2', '05.557 - Àlgebra - Aula 2', 220, true)",
        ALGEBRA);
    r1 = challenge(565757, 3, "Repte 1. T'atreveixes a resoldre una equació de segon grau?");
    deliverable(979687, r1, "QÜESTIONARI 1 - Setmana 2", "2026-10-02T21:59:59Z", 15, true);
    deliverable(979674, r1, "Enregistraments R1", "2026-10-11T21:59:59Z", 0, false);
    page(r1, "enunciat-de-lactivitat-r1", "STATEMENT", statement());
  }

  @Test
  void extractsAndStoresTheSpec() {
    answer("algebra-r1-spec.json");

    ExtractionReport report = run.run();

    assertThat(report.items())
        .singleElement()
        .satisfies(i -> assertThat(i.outcome()).isEqualTo(Outcome.EXTRACTED));
    assertThat(status(r1)).isEqualTo("EXTRACTED");
    var spec =
        jdbc.queryForMap(
            "SELECT version, model, spec_json::text AS json, user_edited FROM challenge_spec WHERE challenge_id = ?",
            r1);
    assertThat(spec.get("version")).isEqualTo(1);
    assertThat(spec.get("model")).isEqualTo("claude-opus-5-5");
    assertThat((String) spec.get("json")).contains("\"FORBIDDEN\"");
    assertThat(spec.get("user_edited")).isEqualTo(false);
  }

  @Test
  void doesNotCallClaudeAgainForTheSameStatement() {
    answer("algebra-r1-spec.json");
    run.run();
    anthropic.resetRequests();

    ExtractionReport again = run.run();

    assertThat(again.items())
        .singleElement()
        .satisfies(i -> assertThat(i.outcome()).isEqualTo(Outcome.UNCHANGED));
    anthropic.verify(0, anyRequestedFor(anyUrl()));
    assertThat(versions(r1)).isEqualTo(1);
  }

  @Test
  void extractsANewVersionWhenTheStatementChanges() {
    answer("algebra-r1-spec.json");
    run.run();
    jdbc.update(
        "UPDATE canvas_page SET body_html = body_html || '<p>Nova data de lliurament</p>' WHERE challenge_id = ?",
        r1);

    run.run();

    assertThat(versions(r1)).isEqualTo(2);
  }

  @Test
  void marksTheChallengeForReviewAndRemembersTheFailure() {
    answer("algebra-r1-spec-invalid.json");

    ExtractionReport report = run.run();

    assertThat(report.items())
        .singleElement()
        .satisfies(i -> assertThat(i.outcome()).isEqualTo(Outcome.NEEDS_REVIEW));
    assertThat(status(r1)).isEqualTo("NEEDS_REVIEW");
    var stored =
        jdbc.queryForMap(
            "SELECT spec_json, array_to_string(errors, ' | ') AS errors FROM challenge_spec WHERE challenge_id = ?",
            r1);
    assertThat(stored.get("spec_json")).isNull();
    assertThat((String) stored.get("errors")).contains("assignment:123456");

    anthropic.resetRequests();
    run.run();
    anthropic.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void keepsTheStudentsCorrections() {
    answer("algebra-r1-spec.json");
    run.run();
    jdbc.update("UPDATE challenge_spec SET user_edited = true WHERE challenge_id = ?", r1);
    jdbc.update(
        "UPDATE canvas_page SET body_html = body_html || '<p>Canvi</p>' WHERE challenge_id = ?",
        r1);
    anthropic.resetRequests();

    ExtractionReport report = run.run();

    assertThat(report.items())
        .singleElement()
        .satisfies(i -> assertThat(i.outcome()).isEqualTo(Outcome.EDITED_BY_STUDENT));
    anthropic.verify(0, anyRequestedFor(anyUrl()));
    assertThat(versions(r1)).isEqualTo(1);
  }

  @Test
  void skipsChallengesWithoutAStatement() {
    long r2 = challenge(565760, 4, "Repte 2");
    answer("algebra-r1-spec.json");

    ExtractionReport report = run.run();

    assertThat(report.items())
        .filteredOn(i -> i.challengeId() == r2)
        .singleElement()
        .satisfies(i -> assertThat(i.outcome()).isEqualTo(Outcome.NO_STATEMENT));
    assertThat(status(r2)).isEqualTo("SYNCED");
  }

  @Test
  void ignoresCoursesTheStudentDeselected() {
    jdbc.update("UPDATE course SET selected = false WHERE canvas_id = ?", ALGEBRA);

    assertThat(run.run().items()).isEmpty();
    anthropic.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void stopsWithoutTouchingAnythingWhenClaudeIsUnavailable() {
    anthropic.stubFor(
        post(urlPathEqualTo("/v1/messages"))
            .willReturn(
                com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(529)));

    ExtractionReport report = run.run();

    assertThat(report.stoppedBecause()).isNotNull();
    assertThat(status(r1)).isEqualTo("SYNCED");
    assertThat(versions(r1)).isZero();
  }

  @Test
  void reportsWhatItCouldNotReachAfterStopping() {
    long r2 = challenge(565760, 4, "Repte 2");
    anthropic.stubFor(
        post(urlPathEqualTo("/v1/messages"))
            .willReturn(
                com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(529)));

    ExtractionReport report = run.run();

    assertThat(report.items())
        .extracting(ExtractionReport.Item::challengeId, ExtractionReport.Item::outcome)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(r1, Outcome.PENDING),
            org.assertj.core.groups.Tuple.tuple(r2, Outcome.NO_STATEMENT));
    anthropic.verify(1, anyRequestedFor(anyUrl()));
  }

  @Test
  void neverReopensADoneChallenge() {
    jdbc.update("UPDATE challenge SET status = 'DONE' WHERE id = ?", r1);
    answer("algebra-r1-spec.json");

    run.run();

    assertThat(status(r1)).isEqualTo("DONE");
    assertThat(versions(r1)).isEqualTo(1);
  }

  private long challenge(long moduleId, int position, String name) {
    return jdbc.queryForObject(
        "INSERT INTO challenge (course_id, canvas_module_id, position, name, weight_pct, status) VALUES (?, ?, ?, ?, 15, 'SYNCED') RETURNING id",
        Long.class,
        ALGEBRA,
        moduleId,
        position,
        name);
  }

  private void deliverable(
      long id, long challengeId, String name, String due, int points, boolean counts) {
    jdbc.update(
        "INSERT INTO deliverable (canvas_assignment_id, challenge_id, name, due_at, points, counts_for_grade) VALUES (?, ?, ?, ?, ?, ?)",
        id,
        challengeId,
        name,
        Timestamp.from(Instant.parse(due)),
        points,
        counts);
  }

  private void page(long challengeId, String url, String role, String body) {
    jdbc.update(
        "INSERT INTO canvas_page (course_id, page_url, challenge_id, title, role, body_html, body_hash, updated_at) VALUES (?, ?, ?, ?, ?, ?, 'x', now())",
        ALGEBRA,
        url,
        challengeId,
        url,
        role,
        body);
  }

  private String status(long challengeId) {
    return jdbc.queryForObject(
        "SELECT status FROM challenge WHERE id = ?", String.class, challengeId);
  }

  private int versions(long challengeId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM challenge_spec WHERE challenge_id = ?", Integer.class, challengeId);
  }

  private static String statement() throws Exception {
    return MAPPER
        .readTree(Fixtures.json("canvas/algebra-page-enunciat-r1.json"))
        .get("body")
        .asText();
  }

  private static void answer(String fixture) {
    ObjectNode message =
        MAPPER
            .createObjectNode()
            .put("id", "msg_test")
            .put("type", "message")
            .put("role", "assistant")
            .put("model", "claude-opus-5-5")
            .put("stop_reason", "end_turn")
            .putNull("stop_sequence");
    message
        .withArray("content")
        .addObject()
        .put("type", "text")
        .put("text", Fixtures.json("claude/" + fixture));
    message.putObject("usage").put("input_tokens", 4100).put("output_tokens", 900);
    anthropic.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(okJson(message.toString())));
  }
}
