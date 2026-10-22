package edu.uoc.planner.planning;

import edu.uoc.planner.availability.AvailabilityProfile;

/** Hard limits on sessions, from the student's profile. */
public record PlanLimits(int minSessionMinutes, int maxSessionMinutes, double maxDailyHours) {

  public static PlanLimits of(AvailabilityProfile profile) {
    return new PlanLimits(
        profile.minSessionMinutes(), profile.maxSessionMinutes(), profile.maxDailyHours());
  }
}
