package edu.uoc.planner.planning;

import java.time.Instant;

/**
 * Something the student must know but the planner cannot schedule.
 *
 * @param message in Spanish
 */
public record Notice(String taskId, Type type, Instant from, Instant to, String message) {

  public enum Type {
    /** Identity video: the teachers call it, so it has a window, not a session. */
    VIDEO_WINDOW,
    /** The margin before the deadline has already gone: planned up to the real deadline. */
    MARGIN_SKIPPED
  }
}
