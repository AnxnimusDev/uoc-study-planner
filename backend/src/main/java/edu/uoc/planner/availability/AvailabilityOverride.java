package edu.uoc.planner.availability;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A check-in exception for one date. Without times it covers the whole day.
 *
 * <ul>
 *   <li>{@code BUSY}: no study in that range; wins over {@code FREE}.
 *   <li>{@code FREE}: a range opens; a whole free day means no work that day (holiday).
 *   <li>{@code OFFICE_DAY}: the commute applies that day.
 * </ul>
 */
public record AvailabilityOverride(LocalDate date, LocalTime start, LocalTime end, Effect effect) {

  public AvailabilityOverride {
    if ((start == null) != (end == null)) {
      throw new IllegalArgumentException("An override needs both times or none");
    }
    if (start != null && !end.isAfter(start)) {
      throw new IllegalArgumentException("An override must end after it starts");
    }
  }

  public boolean wholeDay() {
    return start == null;
  }

  public enum Effect {
    BUSY,
    FREE,
    OFFICE_DAY
  }
}
