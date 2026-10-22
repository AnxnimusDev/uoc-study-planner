package edu.uoc.planner.auth.internal;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/** Lets the student log in through a real browser and returns the Canvas cookies (ADR 0002). */
@FunctionalInterface
interface BrowserLogin {

  /**
   * Blocks until {@code /api/v1/users/self} answers 200 with JSON, then returns the cookies the
   * browser would send to {@code canvasUrl}.
   *
   * @throws LoginFailedException on timeout or when the student closes the browser
   */
  List<BrowserCookie> capture(
      URI canvasUrl, Duration timeout, Duration pollInterval, boolean headless);
}
