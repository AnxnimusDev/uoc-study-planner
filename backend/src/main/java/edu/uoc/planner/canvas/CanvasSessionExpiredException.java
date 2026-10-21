package edu.uoc.planner.canvas;

/** No session or Canvas answered 401. Never retried: the student has to log in again. */
public final class CanvasSessionExpiredException extends CanvasException {

  public CanvasSessionExpiredException(String message) {
    super(message);
  }
}
