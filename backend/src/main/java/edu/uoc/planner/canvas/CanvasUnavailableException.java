package edu.uoc.planner.canvas;

/** Canvas answered 5xx, timed out or could not be reached, and the retries ran out. */
public final class CanvasUnavailableException extends CanvasException {

  public CanvasUnavailableException(String message) {
    super(message);
  }

  public CanvasUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
