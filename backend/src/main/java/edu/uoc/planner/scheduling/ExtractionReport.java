package edu.uoc.planner.scheduling;

import java.util.List;

/**
 * What one extraction run did.
 *
 * @param stoppedBecause set when Claude could not be asked (no key, API down); the remaining
 *     challenges were left untouched
 */
public record ExtractionReport(List<Item> items, String stoppedBecause) {

  public ExtractionReport {
    items = List.copyOf(items);
  }

  public enum Outcome {
    EXTRACTED,
    NEEDS_REVIEW,
    /** Same statement as the last attempt: Claude not called. */
    UNCHANGED,
    /** No readable statement page. */
    NO_STATEMENT,
    /** The statement changed but the student corrected the last spec: kept, to review by hand. */
    EDITED_BY_STUDENT,
    /** Needs an extraction, but the run had stopped (no key, API down): tried next time. */
    PENDING
  }

  public record Item(long challengeId, String courseCode, String challengeName, Outcome outcome) {}
}
