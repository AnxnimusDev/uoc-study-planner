package edu.uoc.planner.extraction;

/**
 * Claude could not be asked: no API key, network error or API error after the SDK retries. The
 * challenge keeps its state and the extraction is tried again on the next sync.
 */
public final class ExtractionUnavailableException extends RuntimeException {

  public ExtractionUnavailableException(String message) {
    super(message);
  }

  public ExtractionUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
