package edu.uoc.planner.availability;

/** {@code config/availability.yml} is missing or makes no sense. The message says what to fix. */
public final class AvailabilityConfigException extends RuntimeException {

  public AvailabilityConfigException(String message) {
    super(message);
  }

  public AvailabilityConfigException(String message, Throwable cause) {
    super(message, cause);
  }
}
