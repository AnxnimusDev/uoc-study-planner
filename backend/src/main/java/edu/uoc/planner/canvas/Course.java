package edu.uoc.planner.canvas;

/**
 * A course of the current term.
 *
 * @param uuid used by the public calendar feed {@code /feeds/calendars/course_{uuid}.ics}
 * @param startAt first day of the course: "Setmana N" counts 7-day blocks from it; may be null
 */
public record Course(
    long canvasId, String code, String name, long termId, String uuid, java.time.Instant startAt) {}
