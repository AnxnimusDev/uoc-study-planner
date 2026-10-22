package edu.uoc.planner.output;

/**
 * Supplies the current plan to the calendar feeds. Implemented by whoever stores the plan, so
 * {@code output} depends on no persistence (same pattern as ADR 0005). Without an implementation
 * the feeds are valid but empty.
 */
@FunctionalInterface
public interface CalendarSource {

  CalendarSnapshot current();
}
