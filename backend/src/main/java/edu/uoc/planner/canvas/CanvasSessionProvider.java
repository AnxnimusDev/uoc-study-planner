package edu.uoc.planner.canvas;

import java.util.Optional;

/**
 * Supplies the Canvas session cookie. Implemented by {@code auth} (ADR 0005); {@code canvas} never
 * depends on {@code auth}.
 */
@FunctionalInterface
public interface CanvasSessionProvider {

  /**
   * Value for the {@code Cookie} header, already decrypted, or empty when there is no session.
   * Called before every request, so implementations must not cache an expired value.
   */
  Optional<String> currentCookie();
}
