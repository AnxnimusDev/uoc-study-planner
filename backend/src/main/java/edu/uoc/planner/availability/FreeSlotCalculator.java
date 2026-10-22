package edu.uoc.planner.availability;

import edu.uoc.planner.availability.AvailabilityOverride.Effect;
import edu.uoc.planner.availability.AvailabilityProfile.WorkDay;
import edu.uoc.planner.availability.AvailabilityProfile.WorkMode;
import edu.uoc.planner.availability.internal.DayIntervals;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Free time = waking hours − work and lunch − commute on office days − fixed activities, then the
 * check-in overrides: timed {@code FREE} opens, {@code BUSY} closes and wins (docs/design.md,
 * section 7). Computed in local time and returned as instants, so daylight saving is handled.
 */
public final class FreeSlotCalculator {

  /**
   * @param to exclusive
   */
  public List<FreeSlot> freeSlots(
      AvailabilityProfile profile,
      List<AvailabilityOverride> overrides,
      LocalDate from,
      LocalDate to) {
    List<FreeSlot> slots = new ArrayList<>();
    for (LocalDate date = from; date.isBefore(to); date = date.plusDays(1)) {
      LocalDate day = date;
      List<AvailabilityOverride> today =
          overrides.stream().filter(o -> o.date().equals(day)).toList();
      slots.addAll(day(profile, day, today));
    }
    return slots;
  }

  private static List<FreeSlot> day(
      AvailabilityProfile profile, LocalDate date, List<AvailabilityOverride> overrides) {
    DayOfWeek weekday = date.getDayOfWeek();
    if (profile.noStudyDays().contains(weekday)
        || overrides.stream().anyMatch(o -> o.wholeDay() && o.effect() == Effect.BUSY)) {
      return List.of();
    }
    boolean dayOff = overrides.stream().anyMatch(o -> o.wholeDay() && o.effect() == Effect.FREE);
    WorkDay work = dayOff ? null : profile.workDays().get(weekday);

    DayIntervals free = DayIntervals.between(profile.wakeUp(), profile.bedtime());
    if (work != null) {
      free.subtract(work.start(), work.end());
      if (profile.lunch() != null) {
        free.subtract(profile.lunch().start(), profile.lunch().end());
      }
      boolean office =
          work.mode() == WorkMode.OFFICE
              || overrides.stream().anyMatch(o -> o.effect() == Effect.OFFICE_DAY);
      if (office && !profile.commute().canStudyDuringCommute()) {
        int commute = profile.commute().minutesEachWay();
        int start = DayIntervals.minutes(work.start());
        int end = DayIntervals.minutes(work.end());
        free.subtract(start - commute, start);
        free.subtract(end, end + commute);
      }
    }
    profile.fixedActivities().stream()
        .filter(a -> a.days().contains(weekday))
        .forEach(a -> free.subtract(a.start(), a.end()));
    overrides.stream()
        .filter(o -> !o.wholeDay() && o.effect() == Effect.FREE)
        .forEach(o -> free.add(o.start(), o.end()));
    overrides.stream()
        .filter(o -> !o.wholeDay() && o.effect() == Effect.BUSY)
        .forEach(o -> free.subtract(o.start(), o.end()));
    free.dropShorterThan(profile.minSessionMinutes());

    DayIntervals preferred = new DayIntervals();
    profile.preferredSlots().stream()
        .filter(p -> p.days().contains(weekday))
        .forEach(p -> preferred.add(p.start(), p.end()));

    List<FreeSlot> slots = new ArrayList<>();
    for (int[] range : free.ranges()) {
      split(range, preferred.ranges(), profile, date, slots);
    }
    return slots;
  }

  /** Cuts a free range where preferred slots begin and end, flagging the preferred parts. */
  private static void split(
      int[] range,
      List<int[]> preferred,
      AvailabilityProfile profile,
      LocalDate date,
      List<FreeSlot> slots) {
    int cursor = range[0];
    for (int[] p : preferred) {
      int from = Math.max(range[0], p[0]);
      int to = Math.min(range[1], p[1]);
      if (from >= to) {
        continue;
      }
      if (cursor < from) {
        slots.add(slot(profile, date, cursor, from, false));
      }
      slots.add(slot(profile, date, from, to, true));
      cursor = to;
    }
    if (cursor < range[1]) {
      slots.add(slot(profile, date, cursor, range[1], false));
    }
  }

  private static FreeSlot slot(
      AvailabilityProfile profile, LocalDate date, int start, int end, boolean preferred) {
    return new FreeSlot(instant(profile, date, start), instant(profile, date, end), preferred);
  }

  private static Instant instant(AvailabilityProfile profile, LocalDate date, int minute) {
    return ZonedDateTime.of(date.atStartOfDay().plusMinutes(minute), profile.zone()).toInstant();
  }
}
