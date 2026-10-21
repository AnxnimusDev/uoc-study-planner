package edu.uoc.planner.canvas;

/** Canvas answered 429 and the retries ran out. */
public final class CanvasRateLimitedException extends CanvasException {

  public CanvasRateLimitedException(String message) {
    super(message);
  }
}
