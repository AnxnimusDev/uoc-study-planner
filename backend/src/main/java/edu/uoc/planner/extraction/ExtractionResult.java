package edu.uoc.planner.extraction;

import java.util.List;

/** Outcome of an extraction. */
public sealed interface ExtractionResult {

  /** Model that answered; may differ from the configured one after a refusal fallback. */
  String model();

  /** SHA-256 of the normalized input: extract again only when it changes. */
  String sourceHash();

  /** Valid spec, ready to store in {@code challenge_spec}. */
  record Extracted(ChallengeSpec spec, String specJson, String model, String sourceHash)
      implements ExtractionResult {}

  /**
   * Still invalid after the retry, or refused: the challenge goes to {@code NEEDS_REVIEW}.
   *
   * @param lastResponse raw text of the last answer, for manual editing; may be empty
   */
  record NeedsReview(List<String> errors, String lastResponse, String model, String sourceHash)
      implements ExtractionResult {

    public NeedsReview {
      errors = List.copyOf(errors);
    }
  }
}
