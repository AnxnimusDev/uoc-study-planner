package edu.uoc.planner.extraction.internal;

/** One structured-output call to the model. */
public interface SpecModel {

  /**
   * @throws edu.uoc.planner.extraction.ExtractionUnavailableException when the model cannot be
   *     reached
   */
  ModelReply complete(String userMessage);

  /**
   * @param stopReason as sent by the API: {@code end_turn}, {@code max_tokens}, {@code refusal}...
   * @param refusalCategory only when {@code stopReason} is {@code refusal}
   */
  record ModelReply(String text, String stopReason, String model, String refusalCategory) {

    public boolean refused() {
      return "refusal".equals(stopReason);
    }

    public boolean truncated() {
      return "max_tokens".equals(stopReason);
    }
  }
}
