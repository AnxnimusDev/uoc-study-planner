package edu.uoc.planner.canvas;

/** Canvas answered with another 4xx, or with a body that cannot be parsed. */
public final class CanvasUnexpectedResponseException extends CanvasException {

  public CanvasUnexpectedResponseException(String message) {
    super(message);
  }

  public CanvasUnexpectedResponseException(String message, Throwable cause) {
    super(message, cause);
  }
}
