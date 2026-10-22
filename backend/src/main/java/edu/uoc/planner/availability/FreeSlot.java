package edu.uoc.planner.availability;

import java.time.Duration;
import java.time.Instant;

/**
 * Time the student can study. Consecutive slots may touch: a gap is split where a preferred slot
 * starts or ends, so the planner can still join them into one session.
 *
 * @param preferred inside one of the student's preferred study slots (soft constraint)
 */
public record FreeSlot(Instant start, Instant end, boolean preferred) {

  public Duration duration() {
    return Duration.between(start, end);
  }
}
