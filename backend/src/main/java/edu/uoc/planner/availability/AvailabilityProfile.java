package edu.uoc.planner.availability;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The student's typical week ({@code config/availability.yml}). Times are local to {@link #zone()}.
 *
 * @param lunch break on work days only; may be null
 * @param maxSessionMinutes and {@code maxDailyHours} are limits for the planner, not for free time
 * @param marginDaysPerCourse course code ("05.557") → days before {@code due_at} to finish
 */
public record AvailabilityProfile(
    ZoneId zone,
    Map<DayOfWeek, WorkDay> workDays,
    TimeRange lunch,
    Commute commute,
    LocalTime wakeUp,
    LocalTime bedtime,
    List<Activity> fixedActivities,
    List<PreferredSlot> preferredSlots,
    int minSessionMinutes,
    int maxSessionMinutes,
    double maxDailyHours,
    Set<DayOfWeek> noStudyDays,
    int defaultMarginDays,
    Map<String, Integer> marginDaysPerCourse) {

  public AvailabilityProfile {
    workDays = workDays.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(workDays));
    fixedActivities = List.copyOf(fixedActivities);
    preferredSlots = List.copyOf(preferredSlots);
    noStudyDays = Set.copyOf(noStudyDays);
    marginDaysPerCourse = Map.copyOf(marginDaysPerCourse);
  }

  /** Days of margin before the real deadline for a course, or the default. */
  public int marginDays(String courseCode) {
    return marginDaysPerCourse.getOrDefault(courseCode, defaultMarginDays);
  }

  public AvailabilityProfile withWorkDay(DayOfWeek day, WorkDay workDay) {
    Map<DayOfWeek, WorkDay> days = new EnumMap<>(DayOfWeek.class);
    days.putAll(workDays);
    days.put(day, workDay);
    return new AvailabilityProfile(
        zone,
        days,
        lunch,
        commute,
        wakeUp,
        bedtime,
        fixedActivities,
        preferredSlots,
        minSessionMinutes,
        maxSessionMinutes,
        maxDailyHours,
        noStudyDays,
        defaultMarginDays,
        marginDaysPerCourse);
  }

  public AvailabilityProfile withCommute(Commute newCommute) {
    return new AvailabilityProfile(
        zone,
        workDays,
        lunch,
        newCommute,
        wakeUp,
        bedtime,
        fixedActivities,
        preferredSlots,
        minSessionMinutes,
        maxSessionMinutes,
        maxDailyHours,
        noStudyDays,
        defaultMarginDays,
        marginDaysPerCourse);
  }

  public AvailabilityProfile withNoStudyDays(Set<DayOfWeek> days) {
    return new AvailabilityProfile(
        zone,
        workDays,
        lunch,
        commute,
        wakeUp,
        bedtime,
        fixedActivities,
        preferredSlots,
        minSessionMinutes,
        maxSessionMinutes,
        maxDailyHours,
        days,
        defaultMarginDays,
        marginDaysPerCourse);
  }

  public AvailabilityProfile withMinSessionMinutes(int minutes) {
    return new AvailabilityProfile(
        zone,
        workDays,
        lunch,
        commute,
        wakeUp,
        bedtime,
        fixedActivities,
        preferredSlots,
        minutes,
        maxSessionMinutes,
        maxDailyHours,
        noStudyDays,
        defaultMarginDays,
        marginDaysPerCourse);
  }

  public enum WorkMode {
    REMOTE,
    OFFICE
  }

  public record WorkDay(WorkMode mode, LocalTime start, LocalTime end) {}

  public record TimeRange(LocalTime start, LocalTime end) {}

  /**
   * @param canStudyDuringCommute the commute counts as free time (public transport)
   */
  public record Commute(int minutesEachWay, boolean canStudyDuringCommute) {}

  public record Activity(String label, Set<DayOfWeek> days, LocalTime start, LocalTime end) {

    public Activity {
      days = Set.copyOf(days);
    }
  }

  public record PreferredSlot(Set<DayOfWeek> days, LocalTime start, LocalTime end) {

    public PreferredSlot {
      days = Set.copyOf(days);
    }
  }
}
