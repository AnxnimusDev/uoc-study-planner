package edu.uoc.planner.canvas.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.uoc.planner.Fixtures;
import edu.uoc.planner.canvas.Challenge;
import edu.uoc.planner.canvas.Course;
import edu.uoc.planner.canvas.Deliverable;
import edu.uoc.planner.canvas.OtherPage;
import edu.uoc.planner.canvas.Statement;
import edu.uoc.planner.canvas.StudyGuide;
import edu.uoc.planner.canvas.internal.api.CanvasAssignment;
import edu.uoc.planner.canvas.internal.api.CanvasModule;
import edu.uoc.planner.canvas.internal.api.CanvasModuleItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class CanvasNormalizerTest {

  private static final long ALGEBRA = 94063;
  private static final long IPO = 92868;

  private static final ObjectMapper MAPPER = Jackson2ObjectMapperBuilder.json().build();

  private final CanvasNormalizer normalizer = new CanvasNormalizer();

  @Nested
  class Courses {

    @Test
    void keepsOnlyTheCurrentTerm() {
      List<Course> courses = normalizer.courses(read("courses.json", new TypeReference<>() {}));

      assertThat(courses).hasSize(6).extracting(Course::termId).containsOnly(220L);
      assertThat(courses).extracting(Course::canvasId).doesNotContain(22184L);
    }
  }

  @Nested
  class Algebra {

    private final List<Challenge> challenges =
        normalizer.challenges(
            ALGEBRA,
            read("algebra-modules.json", new TypeReference<>() {}),
            read("algebra-assignments.json", new TypeReference<>() {}));

    @Test
    void turnsOnlyRepteModulesIntoChallenges() {
      assertThat(challenges)
          .extracting(Challenge::canvasModuleId)
          .containsExactly(565757L, 565760L, 565766L, 565768L, 565774L);
      assertThat(challenges).extracting(Challenge::position).containsExactly(3, 4, 5, 6, 7);
      assertThat(challenges).allSatisfy(c -> assertThat(c.courseId()).isEqualTo(ALGEBRA));
    }

    @Test
    void weighsEachChallengeByThePointsThatCount() {
      assertThat(challenges)
          .extracting(Challenge::weightPct)
          .containsExactly(pct("15.00"), pct("15.00"), pct("15.00"), pct("15.00"), pct("40.00"));
    }

    @Test
    void findsTheStatementAndTheWeeklyGuide() {
      Challenge r1 = challenges.getFirst();

      assertThat(r1.statement()).map(Statement::pageUrl).hasValue("enunciat-de-lactivitat-r1");
      assertThat(r1.studyGuide())
          .map(StudyGuide::pageUrl)
          .hasValue("ges-guies-destudi-setmanal-r1");
      assertThat(r1.pages())
          .filteredOn(OtherPage.class::isInstance)
          .extracting(p -> p.pageUrl())
          .containsExactly("plantejament-i-competencies-r1", "recursos-daprenentatge-r1");
    }

    @Test
    void resolvesDeliverablesByContentIdInModuleOrder() {
      Challenge r2 = challenges.get(1);

      assertThat(r2.deliverables())
          .extracting(Deliverable::canvasAssignmentId)
          .containsExactly(979698L, 979702L, 979706L, 979710L, 979675L);
      assertThat(r2.deliverables().getFirst().dueAt())
          .isEqualTo(Instant.parse("2026-10-30T22:59:59Z"));
      assertThat(r2.unresolvedAssignmentIds()).isEmpty();
    }

    @Test
    void marksRecordingsThatDoNotCountForTheGrade() {
      Deliverable recordingR2 = deliverable(challenges.get(1), 979675L);
      Deliverable recordingR5 = deliverable(challenges.get(4), 979681L);

      assertThat(recordingR2.countsForGrade()).isFalse();
      assertThat(recordingR2.unlockAt()).isEqualTo(Instant.parse("2026-11-03T23:00:00Z"));
      assertThat(recordingR2.lockAt()).isEqualTo(Instant.parse("2026-11-08T23:30:00Z"));
      assertThat(recordingR5.countsForGrade()).isTrue();
      assertThat(recordingR5.points()).isEqualByComparingTo("0");
    }

    @Test
    void flagsExternalToolQuizzes() {
      assertThat(deliverable(challenges.getFirst(), 979687L).externalTool()).isTrue();
      assertThat(deliverable(challenges.get(4), 979682L).externalTool()).isFalse();
    }

    @Test
    void ignoresDiscussionsAsDeliverables() {
      assertThat(challenges.stream().flatMap(c -> c.deliverables().stream()))
          .extracting(Deliverable::canvasAssignmentId)
          .doesNotContain(2034578L, 2034579L, 2034580L, 2034582L, 2034583L);
    }
  }

  @Nested
  class Ipo {

    private final List<CanvasModule> modules = read("ipo-modules.json", new TypeReference<>() {});

    @Test
    void findsTheStatementByPageUrlWhateverItsTitle() {
      List<Challenge> challenges = normalizer.challenges(IPO, modules, List.of());

      assertThat(challenges).hasSize(4);
      assertThat(challenges.getFirst().statement())
          .map(Statement::title)
          .hasValue("Repte 1. Investigació");
      assertThat(challenges).allSatisfy(c -> assertThat(c.studyGuide()).isEmpty());
      assertThat(challenges.getFirst().pages())
          .extracting(p -> p.pageUrl())
          .contains("estudi-de-cas-aplicacions-de-lectura-de-llibres");
    }

    @Test
    void resolvesTheDeliverableByContentIdAndReportsTheMissingOnes() {
      CanvasAssignment r1 =
          assignment(968028L, "Lliurament de l'activitat R1", "2026-10-25T22:59:59Z", 25, false);

      List<Challenge> challenges = normalizer.challenges(IPO, modules, List.of(r1));

      assertThat(challenges.getFirst().deliverables())
          .extracting(Deliverable::canvasAssignmentId)
          .containsExactly(968028L);
      assertThat(challenges.get(1).deliverables()).isEmpty();
      assertThat(challenges.get(1).unresolvedAssignmentIds()).containsExactly(968031L);
    }

    @Test
    void weighsZeroWhenNothingInTheCourseHasPoints() {
      List<Challenge> challenges = normalizer.challenges(IPO, modules, List.of());

      assertThat(challenges).extracting(Challenge::weightPct).containsOnly(BigDecimal.ZERO);
    }
  }

  @Nested
  class WithoutRepteModules {

    @Test
    void fallsBackToModulesWithADeliverableThatHasADueDate() {
      CanvasModule welcome = module(1L, 1, "Welcome", assignmentItem(10L, 1L));
      CanvasModule unit = module(2L, 2, "Unit 1. Grammar", assignmentItem(20L, 2L));
      List<CanvasAssignment> assignments =
          List.of(
              assignment(10L, "Intro", null, 0, false),
              assignment(20L, "Essay", "2026-11-15T22:59:59Z", 100, false));

      List<Challenge> challenges =
          normalizer.challenges(95209L, List.of(welcome, unit), assignments);

      assertThat(challenges).extracting(Challenge::name).containsExactly("Unit 1. Grammar");
      assertThat(challenges.getFirst().weightPct()).isEqualByComparingTo("100");
    }
  }

  @Nested
  class Weights {

    @Test
    void ignoresPointsOfDeliverablesOmittedFromTheFinalGrade() {
      CanvasModule r1 = module(1L, 1, "Repte 1", assignmentItem(10L, 1L));
      CanvasModule r2 = module(2L, 2, "Repte 2", assignmentItem(20L, 2L));
      List<CanvasAssignment> assignments =
          List.of(
              assignment(10L, "Practice", "2026-11-01T22:59:59Z", 50, true),
              assignment(20L, "Final", "2026-12-01T22:59:59Z", 30, false),
              assignment(30L, "Exam", "2027-01-15T22:59:59Z", 70, false));

      List<Challenge> challenges = normalizer.challenges(1L, List.of(r1, r2), assignments);

      assertThat(challenges)
          .extracting(Challenge::weightPct)
          .containsExactly(pct("0.00"), pct("30.00"));
    }
  }

  private static Deliverable deliverable(Challenge challenge, long assignmentId) {
    return challenge.deliverables().stream()
        .filter(d -> d.canvasAssignmentId() == assignmentId)
        .findFirst()
        .orElseThrow();
  }

  private static BigDecimal pct(String value) {
    return new BigDecimal(value);
  }

  private static <T> T read(String fixture, TypeReference<T> type) {
    try {
      return MAPPER.readValue(Fixtures.json("canvas/" + fixture), type);
    } catch (Exception e) {
      throw new IllegalStateException(fixture, e);
    }
  }

  private static CanvasModule module(long id, int position, String name, CanvasModuleItem item) {
    return new CanvasModule(id, name, position, null, "unlocked", 1, List.of(item));
  }

  private static CanvasModuleItem assignmentItem(long contentId, long moduleId) {
    return new CanvasModuleItem(
        contentId * 10, moduleId, 1, "Item", "Assignment", contentId, null, null, null, null);
  }

  private static CanvasAssignment assignment(
      long id, String name, String dueAt, int points, boolean omitFromFinalGrade) {
    return new CanvasAssignment(
        id,
        null,
        name,
        null,
        1,
        BigDecimal.valueOf(points),
        "points",
        null,
        dueAt == null ? null : Instant.parse(dueAt),
        null,
        null,
        omitFromFinalGrade,
        List.of("online_upload"),
        null,
        true,
        null);
  }
}
