package edu.uoc.planner.scheduling;

import edu.uoc.planner.canvas.CanvasException;
import edu.uoc.planner.canvas.CanvasSessionExpiredException;
import edu.uoc.planner.canvas.CanvasSync;
import edu.uoc.planner.canvas.SyncReport;
import edu.uoc.planner.planning.PlanTrigger;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Flow steps 2–5 in order (docs/design.md, section 4). An expired session stops everything, so the
 * student logs in again; Canvas being down only skips the sync and plans with the stored data.
 * Extraction stopping (no key, API down) does not block the plan either.
 */
@Service
public class StudyPipeline {

  private static final Logger log = LoggerFactory.getLogger(StudyPipeline.class);

  private final CanvasSync canvas;
  private final ExtractionRun extraction;
  private final PlanRun planning;
  private final Clock clock;

  StudyPipeline(CanvasSync canvas, ExtractionRun extraction, PlanRun planning, Clock clock) {
    this.canvas = canvas;
    this.extraction = extraction;
    this.planning = planning;
    this.clock = clock;
  }

  /**
   * @throws CanvasSessionExpiredException without a valid UOC session
   */
  public PipelineReport run(PlanTrigger trigger) {
    SyncReport sync = null;
    String problem = null;
    try {
      sync = canvas.sync();
    } catch (CanvasSessionExpiredException e) {
      throw e;
    } catch (CanvasException e) {
      log.warn("Canvas sync skipped: {}", e.getMessage());
      problem = e.getMessage();
    }
    ExtractionReport extracted = extraction.run();
    PlanReport plan = planning.run(clock.instant(), trigger);
    return new PipelineReport(sync, problem, extracted, plan);
  }
}
