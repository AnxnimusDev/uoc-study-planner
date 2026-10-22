package edu.uoc.planner.auth.internal;

import edu.uoc.planner.canvas.CanvasSessionExpiredEvent;
import edu.uoc.planner.canvas.CanvasSessionProvider;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * {@code auth}'s side of ADR 0005: supplies the stored cookie to {@code canvas} and expires it when
 * Canvas answers 401. The listener is synchronous because the event is published outside a
 * transaction, so the session is already marked when the sync sees the exception.
 */
@Component
final class StoredCanvasSession implements CanvasSessionProvider {

  private static final Logger log = LoggerFactory.getLogger(StoredCanvasSession.class);

  private final SessionStore store;

  StoredCanvasSession(SessionStore store) {
    this.store = store;
  }

  @Override
  public Optional<String> currentCookie() {
    return store.currentCookie();
  }

  @EventListener
  void on(CanvasSessionExpiredEvent event) {
    log.info("Canvas session expired at {}, a new login is needed", event.detectedAt());
    store.markExpired(event.detectedAt());
  }
}
