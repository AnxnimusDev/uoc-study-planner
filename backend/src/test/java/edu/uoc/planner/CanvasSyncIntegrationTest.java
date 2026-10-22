package edu.uoc.planner;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import edu.uoc.planner.auth.internal.SessionStore;
import edu.uoc.planner.canvas.CanvasSync;
import edu.uoc.planner.canvas.SyncReport;
import java.math.BigDecimal;
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

/**
 * {@code make sync} end to end: Canvas (WireMock on the real fixtures) → normalization → database.
 * Àlgebra and IPO have fixtures; the other four courses answer with empty lists.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CanvasSyncIntegrationTest {

  private static final long ALGEBRA = 94063;
  private static final long IPO = 92868;
  private static final String JSON = "application/json; charset=utf-8";

  @RegisterExtension
  static WireMockExtension canvas =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void canvasUrl(DynamicPropertyRegistry registry) {
    registry.add("planner.canvas.base-url", canvas::baseUrl);
    registry.add("planner.canvas.requests-per-second", () -> "200");
    registry.add("planner.canvas.max-attempts", () -> "1");
  }

  @Autowired private CanvasSync sync;
  @Autowired private SessionStore sessions;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    jdbc.execute(
        "TRUNCATE canvas_page, deliverable, challenge_spec, study_session, task, challenge, course"
            + " RESTART IDENTITY CASCADE");
    sessions.replace("canvas_session=test", Instant.parse("2026-10-22T08:00:00Z"));
    canvas.resetAll();
    json("/api/v1/users/self", Fixtures.json("canvas/users-self.json"));
    json("/api/v1/courses", Fixtures.json("canvas/courses.json"));
    json("/api/v1/courses/" + ALGEBRA + "/modules", Fixtures.json("canvas/algebra-modules.json"));
    json(
        "/api/v1/courses/" + ALGEBRA + "/assignments",
        Fixtures.json("canvas/algebra-assignments.json"));
    json("/api/v1/courses/" + IPO + "/modules", Fixtures.json("canvas/ipo-modules.json"));
    page(
        ALGEBRA,
        "enunciat-de-lactivitat-r1",
        Fixtures.json("canvas/algebra-page-enunciat-r1.json"));
    page(IPO, "enunciat-de-lactivitat-r1", Fixtures.json("canvas/ipo-page-enunciat-r1.json"));
    canvas.stubFor(
        get(urlPathMatching("/api/v1/courses/[0-9]+/(modules|assignments)"))
            .atPriority(10)
            .willReturn(okForContentType(JSON, "[]")));
  }

  @Test
  void storesTheCoursesOfTheCurrentTermAsSelected() {
    sync.sync();

    assertThat(
            jdbc.queryForList(
                "SELECT canvas_id FROM course WHERE selected ORDER BY canvas_id", Long.class))
        .containsExactly(89934L, 90696L, IPO, ALGEBRA, 94782L, 95209L);
    assertThat(
            jdbc.queryForObject(
                "SELECT ics_url FROM course WHERE canvas_id = ?", String.class, ALGEBRA))
        .startsWith(canvas.baseUrl() + "/feeds/calendars/course_");
  }

  @Test
  void storesChallengesWithTheirWeights() {
    sync.sync();

    assertThat(
            jdbc.queryForList(
                "SELECT weight_pct FROM challenge WHERE course_id = ? ORDER BY position",
                BigDecimal.class,
                ALGEBRA))
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(pct(15), pct(15), pct(15), pct(15), pct(40));
    assertThat(jdbc.queryForList("SELECT DISTINCT status FROM challenge", String.class))
        .containsExactly("SYNCED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM challenge WHERE course_id = ?", Integer.class, IPO))
        .isEqualTo(4);
  }

  @Test
  void storesTheDeliverablesOfEachChallenge() {
    sync.sync();

    assertThat(deliverableCount(ALGEBRA)).isEqualTo(17);
    assertThat(
            jdbc.queryForObject(
                "SELECT due_at FROM deliverable WHERE canvas_assignment_id = 979698",
                Timestamp.class))
        .isEqualTo(Timestamp.from(Instant.parse("2026-10-30T22:59:59Z")));
    assertThat(
            jdbc.queryForObject(
                "SELECT counts_for_grade FROM deliverable WHERE canvas_assignment_id = 979675",
                Boolean.class))
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT array_to_string(submission_types, ',') FROM deliverable WHERE canvas_assignment_id = 979687",
                String.class))
        .isEqualTo("external_tool");
  }

  @Test
  void storesTheStatementLinkedToItsChallenge() {
    sync.sync();

    var row =
        jdbc.queryForMap(
            "SELECT p.role, p.title, length(p.body_html) AS body, p.body_hash, c.canvas_module_id"
                + " FROM canvas_page p JOIN challenge c ON c.id = p.challenge_id"
                + " WHERE p.course_id = ? AND p.page_url = 'enunciat-de-lactivitat-r1'",
            ALGEBRA);
    assertThat(row.get("role")).isEqualTo("STATEMENT");
    assertThat(row.get("canvas_module_id")).isEqualTo(565757L);
    assertThat((Integer) row.get("body")).isPositive();
    assertThat((String) row.get("body_hash")).matches("[0-9a-f]{64}");
  }

  @Test
  void reportsMissingPagesAndUnresolvedDeliverables() {
    SyncReport report = sync.sync();

    SyncReport.CourseSync algebra = course(report, ALGEBRA);
    assertThat(algebra.challenges()).isEqualTo(5);
    assertThat(algebra.deliverables()).isEqualTo(17);
    assertThat(algebra.pagesStored()).isEqualTo(1);
    assertThat(algebra.missingPages())
        .contains("ges-guies-destudi-setmanal-r1", "plantejament-i-competencies-r1");
    assertThat(course(report, IPO).unresolvedAssignments()).contains(968028L);
  }

  @Test
  void aSecondSyncChangesNothingAndKeepsTheStudentsChoices() {
    sync.sync();
    jdbc.update("UPDATE challenge SET status = 'EXTRACTED' WHERE canvas_module_id = 565757");
    jdbc.update("UPDATE course SET selected = false WHERE canvas_id = ?", IPO);
    canvas.resetRequests();

    SyncReport again = sync.sync();

    assertThat(course(again, ALGEBRA).pagesChanged()).isZero();
    assertThat(deliverableCount(ALGEBRA)).isEqualTo(17);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM challenge WHERE canvas_module_id = 565757", String.class))
        .isEqualTo("EXTRACTED");
    assertThat(
            jdbc.queryForObject(
                "SELECT selected FROM course WHERE canvas_id = ?", Boolean.class, IPO))
        .isFalse();
    assertThat(again.courses()).extracting(SyncReport.CourseSync::courseId).doesNotContain(IPO);
    canvas.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/courses/" + IPO + "/modules")));
  }

  @Test
  void detectsAChangedStatement() throws Exception {
    sync.sync();
    String before = hash(ALGEBRA);
    ObjectNode changed =
        (ObjectNode)
            new ObjectMapper().readTree(Fixtures.json("canvas/algebra-page-enunciat-r1.json"));
    changed.put("body", changed.get("body").asText() + "<p>Data actualitzada</p>");
    page(ALGEBRA, "enunciat-de-lactivitat-r1", changed.toString());

    SyncReport again = sync.sync();

    assertThat(course(again, ALGEBRA).pagesChanged()).isEqualTo(1);
    assertThat(hash(ALGEBRA)).isNotEqualTo(before);
  }

  private String hash(long courseId) {
    return jdbc.queryForObject(
        "SELECT body_hash FROM canvas_page WHERE course_id = ? AND page_url = 'enunciat-de-lactivitat-r1'",
        String.class,
        courseId);
  }

  private int deliverableCount(long courseId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM deliverable d JOIN challenge c ON c.id = d.challenge_id WHERE c.course_id = ?",
        Integer.class,
        courseId);
  }

  private static SyncReport.CourseSync course(SyncReport report, long courseId) {
    return report.courses().stream()
        .filter(c -> c.courseId() == courseId)
        .findFirst()
        .orElseThrow();
  }

  private static BigDecimal pct(int value) {
    return BigDecimal.valueOf(value);
  }

  private static void json(String path, String body) {
    canvas.stubFor(get(urlPathEqualTo(path)).willReturn(okForContentType(JSON, body)));
  }

  private static void page(long courseId, String pageUrl, String body) {
    json("/api/v1/courses/" + courseId + "/pages/" + pageUrl, body);
  }
}
