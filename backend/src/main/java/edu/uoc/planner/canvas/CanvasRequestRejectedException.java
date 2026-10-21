package edu.uoc.planner.canvas;

/** The client refused to send a request: a method other than GET, or a URL outside Canvas. */
public final class CanvasRequestRejectedException extends CanvasException {

  public CanvasRequestRejectedException(String message) {
    super(message);
  }
}
