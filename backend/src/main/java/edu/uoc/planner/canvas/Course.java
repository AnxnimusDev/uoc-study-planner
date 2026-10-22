package edu.uoc.planner.canvas;

/**
 * A course of the current term.
 *
 * @param uuid used by the public calendar feed {@code /feeds/calendars/course_{uuid}.ics}
 */
public record Course(long canvasId, String code, String name, long termId, String uuid) {}
