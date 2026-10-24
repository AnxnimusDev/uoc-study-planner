package edu.uoc.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import edu.uoc.planner.planning.PlannedSession;
import edu.uoc.planner.scheduling.PlanReport;
import edu.uoc.planner.scheduling.PlanRun;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ADR 0007 end to end: a course with no challenges (Laboratori de prototipat) gets its weekly hour
 * in the plan, the database and the calendar feed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PlanRunWeeklyStudyIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-09-24T06:00:00Z");

  @DynamicPropertySource
  static void profileWithWeeklyStudy(DynamicPropertyRegistry registry) {
    try {
      Path profile = Files.createTempFile("availability", ".yml");
      Files.writeString(
          profile,
          Files.readString(Path.of("../config/availability.example.yml"))
              + "\nweeklyStudy:\n  - { course: \"20.043\", minutes: 60, title: \"Laboratori de prototipat\" }\n");
      profile.toFile().deleteOnExit();
      registry.add("planner.availability.file", profile::toString);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Autowired private PlanRun planRun;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MockMvc mvc;
  @Autowired private ApplicationContext context;

  @BeforeEach
  void setUp() {
    jdbc.execute(
        "TRUNCATE plan_notice, study_session, plan_version, task, canvas_page, deliverable, challenge_spec, challenge, course"
            + " RESTART IDENTITY CASCADE");
    jdbc.update(
        "INSERT INTO course (canvas_id, code, name, term_id, selected) VALUES (89934, '20.043 - Laboratori de prototipat - Aula 1', 'Lab', 220, true)");
  }

  @Test
  void reservesAnHourEveryWeekForACourseWithoutChallenges() {
    PlanReport report = planRun.run(NOW);

    Map<String, Duration> perWeek =
        report.plan().sessions().stream()
            .collect(
                Collectors.groupingBy(
                    PlannedSession::taskId,
                    Collectors.reducing(Duration.ZERO, PlannedSession::duration, Duration::plus)));
    assertThat(perWeek)
        .containsKeys("weekly:20.043:2026-W39", "weekly:20.043:2026-W40", "weekly:20.043:2026-W44");
    assertThat(perWeek.values())
        .allSatisfy(d -> assertThat(d).isLessThanOrEqualTo(Duration.ofMinutes(60)));
    assertThat(perWeek.get("weekly:20.043:2026-W40")).isEqualTo(Duration.ofMinutes(60));
    assertThat(report.plan().atRisk()).isEmpty();
    assertThat(report.unknownWeeklyCourses()).isEmpty();
  }

  @Test
  void storesItAndShowsItInThePlanCalendar() throws Exception {
    planRun.run(NOW);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM task WHERE course_id = 89934 AND challenge_id IS NULL",
                Integer.class))
        .isGreaterThanOrEqualTo(6);
    String token =
        (String)
            ReflectionTestUtils.invokeMethod(
                context.getBean(Class.forName("edu.uoc.planner.output.internal.FeedTokenStore")),
                "token");
    mvc.perform(get("/calendar/" + token + "/plan.ics"))
        .andExpect(
            content()
                .string(
                    Matchers.containsString("session-weekly-20.043-2026-W40-1@uoc-study-planner")))
        .andExpect(content().string(Matchers.containsString("Laboratori de prototipat (20.043)")));
  }
}
