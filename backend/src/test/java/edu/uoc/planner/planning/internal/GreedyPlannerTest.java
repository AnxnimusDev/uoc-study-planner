package edu.uoc.planner.planning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import edu.uoc.planner.availability.FreeSlot;
import edu.uoc.planner.extraction.ChallengeSpec.TaskKind;
import edu.uoc.planner.planning.AtRiskTask;
import edu.uoc.planner.planning.Notice;
import edu.uoc.planner.planning.Plan;
import edu.uoc.planner.planning.PlanLimits;
import edu.uoc.planner.planning.PlanRequest;
import edu.uoc.planner.planning.PlanTask;
import edu.uoc.planner.planning.PlannedSession;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Week of Monday 2026-10-26, planned at 08:00 (Madrid). Evenings 18:00–21:00 are preferred slots;
 * mornings 10:00–12:00 are free but not preferred. Limits: sessions 30–120 min, 3 h a day.
 */
class GreedyPlannerTest {

  private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);
  private static final Instant NOW = at(MONDAY, "08:00");
  private static final PlanLimits LIMITS = new PlanLimits(30, 120, 3.0);

  private final GreedyPlanner planner = new GreedyPlanner();

  @Nested
  class Basics {

    @Test
    void placesATaskInTheFirstPreferredSlot() {
      Plan plan = plan(List.of(task("t1").hours(1).build()), evenings(14));

      assertThat(plan.sessions())
          .singleElement()
          .satisfies(
              s -> {
                assertThat(s.start()).isEqualTo(at(MONDAY, "18:00"));
                assertThat(s.end()).isEqualTo(at(MONDAY, "19:00"));
                assertThat(s.taskId()).isEqualTo("t1");
              });
      assertThat(plan.atRisk()).isEmpty();
      assertThat(plan.unscheduled()).isZero();
    }

    @Test
    void splitsLongTasksIntoSessionsWithinTheLimits() {
      Plan plan = plan(List.of(task("t1").hours(5).build()), evenings(14));

      assertThat(total(plan, "t1")).isEqualTo(Duration.ofHours(5));
      assertThat(plan.sessions())
          .allSatisfy(
              s -> {
                assertThat(s.duration()).isBetween(Duration.ofMinutes(30), Duration.ofMinutes(120));
              });
      assertDailyLimit(plan);
      assertNoOverlaps(plan);
    }

    @Test
    void leavesABreakBetweenSessions() {
      Plan plan =
          plan(List.of(task("t1").hours(1).build(), task("t2").hours(1).build()), evenings(14));

      List<PlannedSession> monday = onDay(plan, MONDAY);
      assertThat(monday).hasSize(2);
      assertThat(Duration.between(monday.get(0).end(), monday.get(1).start()))
          .isGreaterThanOrEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void neverLeavesATailShorterThanTheMinimumSession() {
      Plan plan = plan(List.of(task("t1").minutes(130).build()), evenings(14));

      assertThat(plan.sessions())
          .extracting(PlannedSession::duration)
          .allSatisfy(d -> assertThat(d).isGreaterThanOrEqualTo(Duration.ofMinutes(30)));
      assertThat(total(plan, "t1")).isEqualTo(Duration.ofMinutes(130));
    }

    @Test
    void staysInsideTheFreeSlots() {
      List<FreeSlot> slots = evenings(14);
      Plan plan =
          plan(
              List.of(task("a").hours(4).build(), task("b").hours(3).course("05.590").build()),
              slots);

      assertInsideSlots(plan, slots);
      assertNoOverlaps(plan);
    }

    @Test
    void isDeterministic() {
      List<PlanTask> tasks =
          List.of(task("a").hours(2).build(), task("b").hours(2).course("05.590").build());

      assertThat(plan(tasks, evenings(14))).isEqualTo(plan(tasks, evenings(14)));
    }
  }

  @Nested
  class Ordering {

    @Test
    void doesWhatIsDueFirstFirst() {
      PlanTask later = task("later").hours(3).due(MONDAY.plusDays(10)).build();
      PlanTask sooner = task("sooner").hours(3).due(MONDAY.plusDays(4)).build();

      Plan plan = plan(List.of(later, sooner), evenings(14));

      assertThat(first(plan, "sooner")).isBefore(first(plan, "later"));
    }

    @Test
    void breaksTiesByChallengeWeight() {
      PlanTask light = task("light").hours(3).weight(15).challenge(1).build();
      PlanTask heavy = task("heavy").hours(3).weight(40).challenge(2).build();

      Plan plan = plan(List.of(light, heavy), evenings(14));

      assertThat(first(plan, "heavy")).isBefore(first(plan, "light"));
    }

    @Test
    void followsTheRecommendedWeekBeforeTheRealDeadline() {
      LocalDate due = MONDAY.plusDays(25);
      PlanTask q3 = task("q3").hours(1).due(due).recommended(MONDAY.plusDays(13)).build();
      PlanTask q2 = task("q2").hours(1).due(due).recommended(MONDAY.plusDays(6)).build();

      Plan plan = plan(List.of(q3, q2), evenings(28));

      assertThat(first(plan, "q2")).isBefore(first(plan, "q3"));
      assertThat(last(plan, "q2")).isBeforeOrEqualTo(endOf(MONDAY.plusDays(6)));
    }

    @Test
    void waitsUntilTheTaskOpens() {
      PlanTask task = task("t1").hours(1).opens(at(MONDAY.plusDays(2), "00:00")).build();

      assertThat(first(plan(List.of(task), evenings(14)), "t1"))
          .isEqualTo(at(MONDAY.plusDays(2), "18:00"));
    }

    @Test
    void neverPlansBeforeNow() {
      Instant afternoon = at(MONDAY, "19:30");
      Plan plan =
          planner.plan(
              new PlanRequest(
                  afternoon,
                  MADRID,
                  List.of(task("t1").hours(1).build()),
                  evenings(14),
                  LIMITS,
                  6,
                  List.of()));

      assertThat(plan.sessions())
          .allSatisfy(s -> assertThat(s.start()).isAfterOrEqualTo(afternoon));
    }
  }

  @Nested
  class Dependencies {

    @Test
    void startsAfterItsDependenciesEnd() {
      PlanTask read = task("read").hours(4).build();
      PlanTask work = task("work").hours(1).dependsOn("read").build();

      Plan plan = plan(List.of(work, read), evenings(14));

      assertThat(first(plan, "work")).isAfterOrEqualTo(last(plan, "read"));
    }

    @Test
    void forumRepliesWaitTheMinimumDaysAfterTheOwnPost() {
      PlanTask post = task("post").kind(TaskKind.FORUM_POST).hours(1).build();
      PlanTask reply =
          task("reply")
              .kind(TaskKind.FORUM_REPLY)
              .hours(1)
              .dependsOn("post")
              .minDaysAfter(2)
              .build();

      Plan plan = plan(List.of(post, reply), evenings(14));

      assertThat(first(plan, "reply"))
          .isAfterOrEqualTo(last(plan, "post").plus(Duration.ofDays(2)));
    }

    @Test
    void aTaskBlockedByAnUnplannedOneIsAtRisk() {
      PlanTask huge = task("huge").hours(40).due(MONDAY.plusDays(3)).build();
      PlanTask next = task("next").hours(1).dependsOn("huge").due(MONDAY.plusDays(3)).build();

      Plan plan = plan(List.of(huge, next), evenings(14));

      assertThat(plan.atRisk()).extracting(AtRiskTask::taskId).contains("huge", "next");
      assertThat(sessionsOf(plan, "next")).isEmpty();
      assertThat(risk(plan, "next").reason()).contains("huge");
    }
  }

  @Nested
  class Deadlines {

    @Test
    void finishesBeforeTheMarginOfTheDeadline() {
      LocalDate thursday = MONDAY.plusDays(3);
      PlanTask task = task("t1").hours(9).due(thursday).margin(1).build();

      Plan plan = plan(List.of(task), evenings(14));

      // Monday to Wednesday give 3 x 2 h 45 min; Thursday is inside the margin.
      assertThat(last(plan, "t1")).isBeforeOrEqualTo(endOf(thursday).minus(Duration.ofDays(1)));
      assertThat(risk(plan, "t1").missing()).isEqualTo(Duration.ofMinutes(45));
    }

    @Test
    void marksTheChallengeAtRiskInsteadOfTrimming() {
      PlanTask task = task("t1").hours(10).due(MONDAY.plusDays(1)).margin(0).challenge(7).build();

      Plan plan = plan(List.of(task), evenings(14));

      // Two evenings of 18:00-21:00: 2 h + 15 min break + 45 min each.
      assertThat(total(plan, "t1")).isEqualTo(Duration.ofMinutes(330));
      assertThat(risk(plan, "t1").missing()).isEqualTo(Duration.ofMinutes(270));
      assertThat(plan.atRiskChallengeIds()).containsExactly(7L);
      assertThat(plan.unscheduled()).isEqualTo(Duration.ofMinutes(270));
    }

    @Test
    void aPastDeadlineIsAtRisk() {
      PlanTask late = task("late").hours(1).dueAt(NOW.minus(Duration.ofHours(1))).build();

      Plan plan = plan(List.of(late), evenings(14));

      assertThat(plan.sessions()).isEmpty();
      assertThat(risk(plan, "late").reason()).contains("vencido");
    }

    @Test
    void usesTheRealDeadlineWhenTheMarginHasAlreadyPassed() {
      PlanTask task = task("t1").hours(1).dueAt(at(MONDAY, "23:59")).margin(1).build();

      Plan plan = plan(List.of(task), evenings(14));

      assertThat(sessionsOf(plan, "t1"))
          .singleElement()
          .satisfies(s -> assertThat(s.end()).isBeforeOrEqualTo(at(MONDAY, "23:59")));
      assertThat(plan.notices()).extracting(Notice::type).contains(Notice.Type.MARGIN_SKIPPED);
    }

    @Test
    void defersTasksThatOpenAfterTheHorizon() {
      PlanTask future = task("future").hours(1).opens(at(MONDAY.plusWeeks(8), "00:00")).build();

      Plan plan = plan(List.of(future), evenings(70));

      assertThat(plan.sessions()).isEmpty();
      assertThat(plan.atRisk()).isEmpty();
      assertThat(plan.deferredTaskIds()).containsExactly("future");
    }

    @Test
    void doesNotCallAtRiskWhatDoesNotFitInTheHorizonButIsDueLater() {
      PlanTask big = task("big").hours(200).due(MONDAY.plusWeeks(12)).build();

      Plan plan = plan(List.of(big), evenings(90));

      assertThat(plan.atRisk()).isEmpty();
      assertThat(plan.deferredTaskIds()).containsExactly("big");
      assertThat(plan.sessions())
          .allSatisfy(s -> assertThat(s.end()).isBeforeOrEqualTo(NOW.plus(Duration.ofDays(42))));
    }
  }

  @Nested
  class SoftConstraints {

    @Test
    void prefersTheStudentsPreferredSlots() {
      Plan plan = plan(List.of(task("t1").hours(1).build()), concat(mornings(14), evenings(14)));

      assertThat(first(plan, "t1")).isEqualTo(at(MONDAY, "18:00"));
    }

    @Test
    void usesOtherFreeTimeWhenPreferredSlotsAreNotEnough() {
      PlanTask task = task("t1").hours(5).due(MONDAY).margin(0).build();

      Plan plan = plan(List.of(task), concat(mornings(14), evenings(14)));

      assertThat(total(plan, "t1")).isEqualTo(Duration.ofHours(3));
      assertThat(sessionsOf(plan, "t1")).anyMatch(s -> s.start().equals(at(MONDAY, "10:00")));
    }

    @Test
    void keepsAtMostTwoCoursesADayWhenPossible() {
      List<PlanTask> tasks =
          List.of(
              task("a").course("05.557").hours(1).challenge(1).build(),
              task("b").course("05.562").hours(1).challenge(2).build(),
              task("c").course("05.590").minutes(30).challenge(3).build());

      Plan plan = plan(tasks, evenings(14));

      Map<LocalDate, Set<String>> perDay = new HashMap<>();
      plan.sessions()
          .forEach(
              s ->
                  perDay
                      .computeIfAbsent(s.start().atZone(MADRID).toLocalDate(), d -> new HashSet<>())
                      .add(s.courseCode()));
      assertThat(perDay.values())
          .allSatisfy(courses -> assertThat(courses).hasSizeLessThanOrEqualTo(2));
    }
  }

  @Nested
  class Reviews {

    @Test
    void addsAReviewBeforeAGradedSubmission() {
      PlanTask work = task("work").hours(2).deliverable("assignment:1", true).weight(40).build();
      PlanTask submit =
          task("submit")
              .kind(TaskKind.SUBMISSION)
              .minutes(30)
              .deliverable("assignment:1", true)
              .dependsOn("work")
              .weight(40)
              .build();

      Plan plan = plan(List.of(work, submit), evenings(14));

      List<PlannedSession> reviews = ofKind(plan, TaskKind.REVIEW);
      assertThat(reviews)
          .singleElement()
          .satisfies(
              r -> {
                assertThat(r.duration()).isEqualTo(Duration.ofMinutes(60));
                assertThat(r.start()).isAfterOrEqualTo(last(plan, "work"));
                assertThat(r.end()).isBeforeOrEqualTo(first(plan, "submit"));
              });
    }

    @Test
    void reviewsLightChallengesForHalfAnHour() {
      PlanTask quiz =
          task("quiz")
              .kind(TaskKind.QUIZ)
              .minutes(45)
              .deliverable("assignment:2", true)
              .weight(15)
              .build();

      Plan plan = plan(List.of(quiz), evenings(14));

      assertThat(ofKind(plan, TaskKind.REVIEW))
          .singleElement()
          .satisfies(r -> assertThat(r.duration()).isEqualTo(Duration.ofMinutes(30)));
      assertThat(ofKind(plan, TaskKind.REVIEW).getFirst().end())
          .isBeforeOrEqualTo(first(plan, "quiz"));
    }

    @Test
    void skipsTheReviewForDeliverablesThatDoNotCount() {
      PlanTask practice = task("practice").hours(1).deliverable("assignment:3", false).build();

      assertThat(ofKind(plan(List.of(practice), evenings(14)), TaskKind.REVIEW)).isEmpty();
    }

    @Test
    void doesNotDuplicateAnExtractedReview() {
      PlanTask work = task("work").hours(1).deliverable("assignment:4", true).build();
      PlanTask review =
          task("review")
              .kind(TaskKind.REVIEW)
              .minutes(30)
              .deliverable("assignment:4", true)
              .dependsOn("work")
              .build();

      assertThat(ofKind(plan(List.of(work, review), evenings(14)), TaskKind.REVIEW)).hasSize(1);
    }
  }

  @Nested
  class SpecialCases {

    @Test
    void videoRecordingsAreNoticedNotScheduled() {
      Instant opens = at(MONDAY.plusDays(8), "00:00");
      PlanTask video =
          task("video")
              .kind(TaskKind.VIDEO_RECORDING)
              .minutes(30)
              .opens(opens)
              .due(MONDAY.plusDays(13))
              .build();

      Plan plan = plan(List.of(video), evenings(28));

      assertThat(plan.sessions()).isEmpty();
      assertThat(plan.atRisk()).isEmpty();
      assertThat(plan.notices())
          .singleElement()
          .satisfies(
              n -> {
                assertThat(n.type()).isEqualTo(Notice.Type.VIDEO_WINDOW);
                assertThat(n.from()).isEqualTo(opens);
                assertThat(n.taskId()).isEqualTo("video");
              });
    }

    @Test
    void keepsSessionsFromThePreviousPlan() {
      PlannedSession done =
          new PlannedSession(
              "t1",
              1,
              "05.557",
              "Llegir",
              TaskKind.READING,
              at(MONDAY, "18:00"),
              at(MONDAY, "19:00"),
              true);
      PlanTask task = task("t1").kind(TaskKind.READING).hours(2).build();

      Plan plan =
          planner.plan(
              new PlanRequest(NOW, MADRID, List.of(task), evenings(14), LIMITS, 6, List.of(done)));

      assertThat(plan.sessions()).contains(done);
      assertThat(total(plan, "t1")).isEqualTo(Duration.ofHours(2));
      assertNoOverlaps(plan);
    }
  }

  @Nested
  class WithRealAvailability {

    @Test
    void plansAlgebraLikeQuizzesInsideTheExampleWeek() {
      List<FreeSlot> slots =
          new edu.uoc.planner.availability.FreeSlotCalculator()
              .freeSlots(
                  edu.uoc.planner.availability.internal.AvailabilityProfileLoader.load(
                      java.nio.file.Path.of("../config/availability.example.yml")),
                  List.of(),
                  MONDAY,
                  MONDAY.plusWeeks(6));
      List<PlanTask> tasks = new ArrayList<>();
      for (int week = 0; week < 4; week++) {
        tasks.add(
            task("study" + week)
                .hours(3)
                .recommended(MONDAY.plusDays(7L * week + 6))
                .due(MONDAY.plusDays(25))
                .build());
        tasks.add(
            task("quiz" + week)
                .kind(TaskKind.QUIZ)
                .minutes(45)
                .dependsOn("study" + week)
                .deliverable("assignment:" + week, true)
                .recommended(MONDAY.plusDays(7L * week + 6))
                .due(MONDAY.plusDays(25))
                .build());
      }

      Plan plan = plan(tasks, slots);

      assertThat(plan.atRisk()).isEmpty();
      assertNoOverlaps(plan);
      assertDailyLimit(plan);
      assertInsideSlots(plan, slots);
    }
  }

  // ---------------------------------------------------------------------------------------------

  private Plan plan(List<PlanTask> tasks, List<FreeSlot> slots) {
    return planner.plan(new PlanRequest(NOW, MADRID, tasks, slots, LIMITS, 6, List.of()));
  }

  private static List<FreeSlot> evenings(int days) {
    return daily(days, "18:00", "21:00", true);
  }

  private static List<FreeSlot> mornings(int days) {
    return daily(days, "10:00", "12:00", false);
  }

  private static List<FreeSlot> daily(int days, String start, String end, boolean preferred) {
    List<FreeSlot> slots = new ArrayList<>();
    for (int i = 0; i < days; i++) {
      LocalDate day = MONDAY.plusDays(i);
      slots.add(new FreeSlot(at(day, start), at(day, end), preferred));
    }
    return slots;
  }

  private static List<FreeSlot> concat(List<FreeSlot> a, List<FreeSlot> b) {
    List<FreeSlot> all = new ArrayList<>(a);
    all.addAll(b);
    all.sort(Comparator.comparing(FreeSlot::start));
    return all;
  }

  private static Instant at(LocalDate day, String time) {
    return ZonedDateTime.of(day, LocalTime.parse(time), MADRID).toInstant();
  }

  private static Instant endOf(LocalDate day) {
    return at(day, "23:59").plusSeconds(59);
  }

  private static List<PlannedSession> sessionsOf(Plan plan, String taskId) {
    return plan.sessions().stream().filter(s -> s.taskId().equals(taskId)).toList();
  }

  private static List<PlannedSession> ofKind(Plan plan, TaskKind kind) {
    return plan.sessions().stream().filter(s -> s.kind() == kind).toList();
  }

  private static List<PlannedSession> onDay(Plan plan, LocalDate day) {
    return plan.sessions().stream()
        .filter(s -> s.start().atZone(MADRID).toLocalDate().equals(day))
        .toList();
  }

  private static Instant first(Plan plan, String taskId) {
    return sessionsOf(plan, taskId).getFirst().start();
  }

  private static Instant last(Plan plan, String taskId) {
    return sessionsOf(plan, taskId).getLast().end();
  }

  private static Duration total(Plan plan, String taskId) {
    return sessionsOf(plan, taskId).stream()
        .map(PlannedSession::duration)
        .reduce(Duration.ZERO, Duration::plus);
  }

  private static AtRiskTask risk(Plan plan, String taskId) {
    return plan.atRisk().stream().filter(r -> r.taskId().equals(taskId)).findFirst().orElseThrow();
  }

  private static void assertNoOverlaps(Plan plan) {
    List<PlannedSession> sorted =
        plan.sessions().stream().sorted(Comparator.comparing(PlannedSession::start)).toList();
    for (int i = 1; i < sorted.size(); i++) {
      assertThat(sorted.get(i).start())
          .as("overlap at %s", sorted.get(i))
          .isAfterOrEqualTo(sorted.get(i - 1).end());
    }
  }

  /** Each session lies inside the union of the free slots (touching slots join). */
  private static void assertInsideSlots(Plan plan, List<FreeSlot> slots) {
    List<Instant[]> merged = new ArrayList<>();
    slots.stream()
        .sorted(Comparator.comparing(FreeSlot::start))
        .forEach(
            f -> {
              if (!merged.isEmpty() && !merged.getLast()[1].isBefore(f.start())) {
                if (f.end().isAfter(merged.getLast()[1])) {
                  merged.getLast()[1] = f.end();
                }
              } else {
                merged.add(new Instant[] {f.start(), f.end()});
              }
            });
    assertThat(plan.sessions())
        .allSatisfy(
            s ->
                assertThat(merged)
                    .anyMatch(m -> !s.start().isBefore(m[0]) && !s.end().isAfter(m[1])));
  }

  private static void assertDailyLimit(Plan plan) {
    Map<LocalDate, Duration> perDay =
        plan.sessions().stream()
            .collect(
                Collectors.groupingBy(
                    s -> s.start().atZone(MADRID).toLocalDate(),
                    Collectors.reducing(Duration.ZERO, PlannedSession::duration, Duration::plus)));
    assertThat(perDay.values())
        .allSatisfy(d -> assertThat(d).isLessThanOrEqualTo(Duration.ofHours(3)));
  }

  private static TaskBuilder task(String id) {
    return new TaskBuilder(id);
  }

  /** Defaults: Àlgebra, weight 20, due in three weeks with one day of margin, graded nothing. */
  private static final class TaskBuilder {
    private final String id;
    private long challengeId = 1;
    private String course = "05.557";
    private double weight = 20;
    private TaskKind kind = TaskKind.WORK;
    private Duration estimate = Duration.ofHours(1);
    private Instant opensAt;
    private Instant dueAt = at(MONDAY.plusDays(21), "22:59");
    private int margin = 1;
    private Instant recommendedBy;
    private final List<String> dependsOn = new ArrayList<>();
    private int minDaysAfter;
    private String deliverableRef;
    private boolean graded;

    private TaskBuilder(String id) {
      this.id = id;
    }

    TaskBuilder challenge(long value) {
      challengeId = value;
      return this;
    }

    TaskBuilder course(String value) {
      course = value;
      return this;
    }

    TaskBuilder weight(double value) {
      weight = value;
      return this;
    }

    TaskBuilder kind(TaskKind value) {
      kind = value;
      return this;
    }

    TaskBuilder hours(double value) {
      estimate = Duration.ofMinutes(Math.round(value * 60));
      return this;
    }

    TaskBuilder minutes(int value) {
      estimate = Duration.ofMinutes(value);
      return this;
    }

    TaskBuilder opens(Instant value) {
      opensAt = value;
      return this;
    }

    TaskBuilder due(LocalDate value) {
      dueAt = endOf(value);
      return this;
    }

    TaskBuilder dueAt(Instant value) {
      dueAt = value;
      return this;
    }

    TaskBuilder margin(int value) {
      margin = value;
      return this;
    }

    TaskBuilder recommended(LocalDate value) {
      recommendedBy = endOf(value);
      return this;
    }

    TaskBuilder dependsOn(String value) {
      dependsOn.add(value);
      return this;
    }

    TaskBuilder minDaysAfter(int value) {
      minDaysAfter = value;
      return this;
    }

    TaskBuilder deliverable(String ref, boolean counts) {
      deliverableRef = ref;
      graded = counts;
      return this;
    }

    PlanTask build() {
      return new PlanTask(
          id,
          challengeId,
          course,
          weight,
          0,
          "Tasca " + id,
          kind,
          estimate,
          opensAt,
          dueAt,
          margin,
          recommendedBy,
          dependsOn,
          minDaysAfter,
          deliverableRef,
          graded);
    }
  }
}
