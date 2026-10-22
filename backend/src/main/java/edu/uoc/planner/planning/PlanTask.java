package edu.uoc.planner.planning;

import edu.uoc.planner.extraction.ChallengeSpec.TaskKind;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * A task to plan, independent of where it came from (extracted spec, external quiz...).
 *
 * @param id unique within the request; {@code dependsOn} refers to these ids
 * @param challengeWeightPct share of the course grade, used to break deadline ties
 * @param order position within the challenge, last tie breaker
 * @param opensAt {@code unlock_at}; null when already open
 * @param dueAt real deadline ({@code due_at}); null when there is none
 * @param marginDays days before {@code dueAt} to be done (effective deadline)
 * @param recommendedBy soft deadline from the study guide or the title ("Setmana 4"); may be null
 * @param minDaysAfterDependency e.g. forum replies wait after the own post
 * @param deliverableRef {@code assignment:<id>}; tasks sharing a graded one get a review before it
 * @param graded the deliverable counts for the grade and has points
 */
public record PlanTask(
    String id,
    long challengeId,
    String courseCode,
    double challengeWeightPct,
    int order,
    String title,
    TaskKind kind,
    Duration estimate,
    Instant opensAt,
    Instant dueAt,
    int marginDays,
    Instant recommendedBy,
    List<String> dependsOn,
    int minDaysAfterDependency,
    String deliverableRef,
    boolean graded) {

  /** External (Wiris) quizzes have no readable statement: planned with this duration. */
  public static final Duration EXTERNAL_QUIZ = Duration.ofMinutes(45);

  public PlanTask {
    dependsOn = List.copyOf(dependsOn);
  }

  public PlanTask withDependency(String taskId) {
    List<String> deps = new java.util.ArrayList<>(dependsOn);
    deps.add(taskId);
    return new PlanTask(
        id,
        challengeId,
        courseCode,
        challengeWeightPct,
        order,
        title,
        kind,
        estimate,
        opensAt,
        dueAt,
        marginDays,
        recommendedBy,
        deps,
        minDaysAfterDependency,
        deliverableRef,
        graded);
  }
}
