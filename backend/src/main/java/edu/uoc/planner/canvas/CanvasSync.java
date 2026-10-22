package edu.uoc.planner.canvas;

import edu.uoc.planner.canvas.internal.CanvasMirror;
import org.springframework.stereotype.Service;

/**
 * One full read of Canvas into the database (docs/design.md, section 5, flow step 2). Read-only
 * against Canvas; only GET requests (CLAUDE.md rule 1).
 */
@Service
public final class CanvasSync {

  private final CanvasMirror mirror;

  CanvasSync(CanvasMirror mirror) {
    this.mirror = mirror;
  }

  /**
   * @throws CanvasSessionExpiredException without a valid session: nothing is written
   * @throws CanvasException when Canvas cannot be read
   */
  public SyncReport sync() {
    return mirror.sync();
  }
}
