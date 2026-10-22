package edu.uoc.planner.scheduling.internal;

import edu.uoc.planner.availability.AvailabilityProfile;
import edu.uoc.planner.canvas.Deliverable;
import edu.uoc.planner.canvas.StoredChallenge;
import edu.uoc.planner.extraction.ChallengeSpec;
import edu.uoc.planner.extraction.ChallengeSpec.TaskKind;
import edu.uoc.planner.planning.PlanTask;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stored challenges and their specs → planner input (docs/design.md, sections 5 and 8).
 *
 * <ul>
 *   <li>A task takes the deadline and opening of its deliverable, or the challenge's.
 *   <li>"Setmana N" in a deliverable title sets the recommended date: end of the N-th 7-day block
 *       from the course start.
 *   <li>External (Wiris) quizzes the spec does not cover become 45-minute quizzes.
 *   <li>Anything already due is left out: submissions are not read yet, so it may be done.
 * </ul>
 */
public final class PlanTaskAdapter {

  private static final Pattern WEEK =
      Pattern.compile("(?:setmana|semana|week)\\s*(\\d{1,2})", Pattern.CASE_INSENSITIVE);

  /**
   * @param unestimated deliverables with no spec and no default duration, "05.557 · name"
   */
  public record Result(List<PlanTask> tasks, List<String> unestimated) {}

  private PlanTaskAdapter() {}

  public static Result adapt(
      List<StoredChallenge> challenges,
      Map<Long, ChallengeSpec> specs,
      AvailabilityProfile profile,
      Instant now) {
    List<PlanTask> tasks = new ArrayList<>();
    List<String> unestimated = new ArrayList<>();
    for (StoredChallenge challenge : challenges) {
      adaptChallenge(challenge, specs.get(challenge.id()), profile, tasks, unestimated);
    }
    List<PlanTask> open =
        tasks.stream().filter(t -> t.dueAt() == null || t.dueAt().isAfter(now)).toList();
    return new Result(open, unestimated);
  }

  private static void adaptChallenge(
      StoredChallenge challenge,
      ChallengeSpec spec,
      AvailabilityProfile profile,
      List<PlanTask> out,
      List<String> unestimated) {
    String course = challenge.courseShortCode();
    int margin = profile.marginDays(course);
    double weight = challenge.weightPct() == null ? 0 : challenge.weightPct().doubleValue();
    Map<String, Deliverable> byRef = new HashMap<>();
    challenge.deliverables().forEach(d -> byRef.put(ref(d), d));
    Instant challengeDue =
        challenge.deliverables().stream()
            .filter(Deliverable::countsForGrade)
            .map(Deliverable::dueAt)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .or(
                () ->
                    challenge.deliverables().stream()
                        .map(Deliverable::dueAt)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder()))
            .orElse(null);
    Instant challengeOpens =
        challenge.deliverables().stream()
            .map(Deliverable::unlockAt)
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder())
            .orElse(null);

    Set<String> covered = new HashSet<>();
    if (spec != null) {
      String prefix = challenge.id() + ":";
      int order = 0;
      for (ChallengeSpec.Task t : spec.tasks()) {
        Deliverable d = t.deliverableRef() == null ? null : byRef.get(t.deliverableRef());
        if (d != null) {
          covered.add(t.deliverableRef());
        }
        out.add(
            new PlanTask(
                prefix + t.id(),
                challenge.id(),
                course,
                weight,
                order++,
                t.title(),
                t.kind(),
                Duration.ofMinutes(Math.round(t.estimatedHours() * 60)),
                d != null && d.unlockAt() != null ? d.unlockAt() : challengeOpens,
                d != null && d.dueAt() != null ? d.dueAt() : challengeDue,
                margin,
                d == null ? null : recommended(d.name(), challenge.courseStartAt()),
                t.dependsOn().stream().map(dep -> prefix + dep).toList(),
                t.minDaysAfterDependency() == null ? 0 : t.minDaysAfterDependency(),
                d == null ? null : t.deliverableRef(),
                d != null && graded(d)));
      }
    }
    for (Deliverable d : challenge.deliverables()) {
      if (covered.contains(ref(d))) {
        continue;
      }
      if (d.submissionTypes().equals(List.of("external_tool"))) {
        out.add(
            new PlanTask(
                "quiz:" + d.canvasAssignmentId(),
                challenge.id(),
                course,
                weight,
                1000,
                d.name(),
                TaskKind.QUIZ,
                PlanTask.EXTERNAL_QUIZ,
                d.unlockAt(),
                d.dueAt(),
                margin,
                recommended(d.name(), challenge.courseStartAt()),
                List.of(),
                0,
                ref(d),
                graded(d)));
      } else if (spec == null && d.countsForGrade()) {
        unestimated.add(course + " · " + d.name());
      }
    }
  }

  /** End of the N-th 7-day block from the course start, for titles such as "Setmana 3". */
  static Instant recommended(String title, Instant courseStart) {
    if (courseStart == null || title == null) {
      return null;
    }
    Matcher week = WEEK.matcher(title);
    return week.find()
        ? courseStart.plus(Duration.ofDays(7L * Integer.parseInt(week.group(1))))
        : null;
  }

  private static boolean graded(Deliverable d) {
    return d.countsForGrade() && d.points() != null && d.points().signum() > 0;
  }

  private static String ref(Deliverable d) {
    return "assignment:" + d.canvasAssignmentId();
  }
}
