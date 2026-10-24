package edu.uoc.planner.scheduling.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.uoc.planner.Fixtures;
import edu.uoc.planner.availability.AvailabilityProfile;
import edu.uoc.planner.availability.internal.AvailabilityProfileLoader;
import edu.uoc.planner.canvas.ChallengeStatus;
import edu.uoc.planner.canvas.Deliverable;
import edu.uoc.planner.canvas.StoredChallenge;
import edu.uoc.planner.extraction.ChallengeSpec;
import edu.uoc.planner.extraction.ChallengeSpec.TaskKind;
import edu.uoc.planner.planning.PlanTask;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlanTaskAdapterTest {

  private static final Instant COURSE_START = Instant.parse("2026-09-22T22:00:00Z");
  private static final Instant NOW = Instant.parse("2026-09-24T08:00:00Z");
  private static final AvailabilityProfile PROFILE =
      AvailabilityProfileLoader.load(Path.of("../config/availability.example.yml"));

  private static final Deliverable QUIZ_1 =
      deliverable(
          979687,
          "QÜESTIONARI 1 - Setmana 2",
          "2026-09-22T22:00:00Z",
          "2026-10-02T21:59:59Z",
          15,
          true,
          "external_tool");
  private static final Deliverable VIDEO_1 =
      deliverable(
          979674,
          "Enregistraments R1 -- Vídeo",
          "2026-10-06T22:00:00Z",
          "2026-10-11T21:59:59Z",
          0,
          false,
          "online_text_entry",
          "media_recording");
  private static final Deliverable QUIZ_2 =
      deliverable(
          979698,
          "QÜESTIONARI 2 - Setmana 3",
          "2026-10-02T22:00:00Z",
          "2026-10-30T22:59:59Z",
          3.75,
          true,
          "external_tool");

  @Test
  void turnsSpecTasksIntoPlanTasks() throws Exception {
    PlanTaskAdapter.Result result = adapt(List.of(r1()), Map.of(1L, spec()));

    assertThat(result.tasks())
        .extracting(PlanTask::id)
        .containsExactly("1:t1", "1:t2", "1:t3", "1:t4");
    PlanTask quiz = task(result, "1:t3");
    assertThat(quiz.kind()).isEqualTo(TaskKind.QUIZ);
    assertThat(quiz.dependsOn()).containsExactly("1:t2");
    assertThat(quiz.estimate()).isEqualTo(Duration.ofHours(1));
    assertThat(quiz.dueAt()).isEqualTo(QUIZ_1.dueAt());
    assertThat(quiz.opensAt()).isEqualTo(QUIZ_1.unlockAt());
    assertThat(quiz.deliverableRef()).isEqualTo("assignment:979687");
    assertThat(quiz.graded()).isTrue();
    assertThat(quiz.courseCode()).isEqualTo("05.557");
    assertThat(quiz.challengeWeightPct()).isEqualTo(15.0);
    assertThat(quiz.marginDays()).isEqualTo(1);
  }

  @Test
  void recommendsTheWeekInTheDeliverableTitle() throws Exception {
    PlanTask quiz = task(adapt(List.of(r1()), Map.of(1L, spec())), "1:t3");

    // "Setmana 2" ends two weeks after the course starts.
    assertThat(quiz.recommendedBy()).isEqualTo(COURSE_START.plus(Duration.ofDays(14)));
  }

  @Test
  void tasksWithoutADeliverableTakeTheChallengeDeadline() throws Exception {
    PlanTask reading = task(adapt(List.of(r1()), Map.of(1L, spec())), "1:t1");

    assertThat(reading.deliverableRef()).isNull();
    assertThat(reading.dueAt()).isEqualTo(QUIZ_1.dueAt());
    assertThat(reading.opensAt()).isEqualTo(QUIZ_1.unlockAt());
  }

  @Test
  void ungradedDeliverablesGetNoReview() throws Exception {
    assertThat(task(adapt(List.of(r1()), Map.of(1L, spec())), "1:t4").graded()).isFalse();
  }

  @Test
  void plansExternalQuizzesWithoutASpecFor45Minutes() {
    PlanTaskAdapter.Result result = adapt(List.of(r2(QUIZ_2)), Map.of());

    assertThat(result.tasks())
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.id()).isEqualTo("quiz:979698");
              assertThat(t.kind()).isEqualTo(TaskKind.QUIZ);
              assertThat(t.estimate()).isEqualTo(PlanTask.EXTERNAL_QUIZ);
              assertThat(t.recommendedBy()).isEqualTo(COURSE_START.plus(Duration.ofDays(21)));
            });
  }

  @Test
  void doesNotDuplicateAQuizTheSpecAlreadyPlans() throws Exception {
    assertThat(adapt(List.of(r1()), Map.of(1L, spec())).tasks())
        .extracting(PlanTask::id)
        .doesNotContain("quiz:979687");
  }

  @Test
  void listsDeliverablesItCannotEstimate() {
    Deliverable report =
        deliverable(
            968028,
            "Lliurament de l'activitat R1",
            null,
            "2026-10-25T22:59:59Z",
            25,
            true,
            "online_upload");

    PlanTaskAdapter.Result result = adapt(List.of(r2(report)), Map.of());

    assertThat(result.tasks()).isEmpty();
    assertThat(result.unestimated()).containsExactly("05.557 · Lliurament de l'activitat R1");
  }

  @Test
  void skipsWhatIsAlreadyDue() throws Exception {
    PlanTaskAdapter.Result later =
        PlanTaskAdapter.adapt(
            List.of(r1()),
            Map.of(1L, spec()),
            PROFILE,
            Set.of("05.557"),
            Instant.parse("2026-10-05T08:00:00Z"));

    assertThat(later.tasks()).extracting(PlanTask::id).containsExactly("1:t4");
  }

  @Test
  void reservesTheWeeklyTimeOfACourseWithoutChallenges() {
    AvailabilityProfile profile =
        PROFILE.withWeeklyStudy(
            List.of(new AvailabilityProfile.WeeklyStudy("20.043", 60, "Laboratori de prototipat")));

    PlanTaskAdapter.Result result =
        PlanTaskAdapter.adapt(List.of(), Map.of(), profile, Set.of("20.043"), NOW);

    // Thursday 2026-09-24 is in ISO week 39; the six-week horizon reaches week 45.
    assertThat(result.tasks())
        .extracting(PlanTask::id)
        .containsExactly(
            "weekly:20.043:2026-W39",
            "weekly:20.043:2026-W40",
            "weekly:20.043:2026-W41",
            "weekly:20.043:2026-W42",
            "weekly:20.043:2026-W43",
            "weekly:20.043:2026-W44",
            "weekly:20.043:2026-W45");
    PlanTask first = result.tasks().getFirst();
    assertThat(first.title()).isEqualTo("Laboratori de prototipat");
    assertThat(first.kind()).isEqualTo(TaskKind.READING);
    assertThat(first.estimate()).isEqualTo(Duration.ofMinutes(60));
    assertThat(first.challengeId()).isZero();
    assertThat(first.courseCode()).isEqualTo("20.043");
    assertThat(first.challengeWeightPct()).isZero();
    assertThat(first.marginDays()).isZero();
    assertThat(first.graded()).isFalse();
    assertThat(first.opensAt()).isEqualTo(Instant.parse("2026-09-20T22:00:00Z"));
    assertThat(first.dueAt()).isEqualTo(Instant.parse("2026-09-27T21:59:59Z"));
  }

  @Test
  void namesTheWeeklyTimeAfterTheCourseByDefault() {
    AvailabilityProfile profile =
        PROFILE.withWeeklyStudy(List.of(new AvailabilityProfile.WeeklyStudy("20.043", 30, null)));

    PlanTask first =
        PlanTaskAdapter.adapt(List.of(), Map.of(), profile, Set.of("20.043"), NOW)
            .tasks()
            .getFirst();

    assertThat(first.title()).isEqualTo("Tiempo semanal · 20.043");
  }

  @Test
  void reportsWeeklyTimeForCoursesThatAreNotSelected() {
    AvailabilityProfile profile =
        PROFILE.withWeeklyStudy(List.of(new AvailabilityProfile.WeeklyStudy("20.999", 60, null)));

    PlanTaskAdapter.Result result =
        PlanTaskAdapter.adapt(List.of(), Map.of(), profile, Set.of("20.043"), NOW);

    assertThat(result.tasks()).isEmpty();
    assertThat(result.unknownWeeklyCourses()).containsExactly("20.999");
  }

  private static PlanTaskAdapter.Result adapt(
      List<StoredChallenge> challenges, Map<Long, ChallengeSpec> specs) {
    return PlanTaskAdapter.adapt(challenges, specs, PROFILE, Set.of("05.557"), NOW);
  }

  private static PlanTask task(PlanTaskAdapter.Result result, String id) {
    return result.tasks().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
  }

  private static StoredChallenge r1() {
    return challenge(1, 3, "Repte 1", List.of(QUIZ_1, VIDEO_1));
  }

  private static StoredChallenge r2(Deliverable deliverable) {
    return challenge(2, 4, "Repte 2", List.of(deliverable));
  }

  private static StoredChallenge challenge(
      long id, int position, String name, List<Deliverable> deliverables) {
    return new StoredChallenge(
        id,
        94063,
        "05.557 - Àlgebra - Aula 2",
        COURSE_START,
        position,
        name,
        BigDecimal.valueOf(15),
        ChallengeStatus.EXTRACTED,
        "<p>Enunciat</p>",
        null,
        null,
        deliverables);
  }

  private static ChallengeSpec spec() throws Exception {
    return new ObjectMapper()
        .readValue(Fixtures.json("claude/algebra-r1-spec.json"), ChallengeSpec.class);
  }

  private static Deliverable deliverable(
      long id,
      String name,
      String unlock,
      String due,
      double points,
      boolean counts,
      String... types) {
    return new Deliverable(
        id,
        name,
        unlock == null ? null : Instant.parse(unlock),
        Instant.parse(due),
        null,
        BigDecimal.valueOf(points),
        counts,
        List.of(types));
  }
}
