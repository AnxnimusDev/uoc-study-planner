package edu.uoc.planner.canvas;

/** Failure talking to Canvas. */
public abstract sealed class CanvasException extends RuntimeException
    permits CanvasSessionExpiredException,
        CanvasRateLimitedException,
        CanvasUnavailableException,
        CanvasRequestRejectedException,
        CanvasUnexpectedResponseException {

  protected CanvasException(String message) {
    super(message);
  }

  protected CanvasException(String message, Throwable cause) {
    super(message, cause);
  }
}
