package edu.uoc.planner.planning;

import edu.uoc.planner.extraction.ChallengeSpec.TaskKind;
import java.time.Duration;
import java.time.Instant;

/**
 * @param kept carried over from the previous plan
 */
public record PlannedSession(
    String taskId,
    long challengeId,
    String courseCode,
    String title,
    TaskKind kind,
    Instant start,
    Instant end,
    boolean kept) {

  public Duration duration() {
    return Duration.between(start, end);
  }
}
