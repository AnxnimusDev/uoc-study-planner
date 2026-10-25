package edu.uoc.planner.planning;

import edu.uoc.planner.availability.AvailabilityProfile;
import java.time.DayOfWeek;
import java.util.Map;

/**
 * Hard limits on sessions, from the student's profile.
 *
 * @param maxDailyHoursPerDay weekdays whose limit differs from {@code maxDailyHours}
 */
public record PlanLimits(
    int minSessionMinutes,
    int maxSessionMinutes,
    double maxDailyHours,
    Map<DayOfWeek, Double> maxDailyHoursPerDay) {

  public PlanLimits {
    maxDailyHoursPerDay = Map.copyOf(maxDailyHoursPerDay);
  }

  public PlanLimits(int minSessionMinutes, int maxSessionMinutes, double maxDailyHours) {
    this(minSessionMinutes, maxSessionMinutes, maxDailyHours, Map.of());
  }

  public static PlanLimits of(AvailabilityProfile profile) {
    return new PlanLimits(
        profile.minSessionMinutes(),
        profile.maxSessionMinutes(),
        profile.maxDailyHours(),
        profile.maxDailyHoursPerDay());
  }

  public long maxDailyMinutes(DayOfWeek day) {
    return Math.round(maxDailyHoursPerDay.getOrDefault(day, maxDailyHours) * 60);
  }
}
