package edu.uoc.planner.extraction;

import java.util.List;

/**
 * Latest row of {@code challenge_spec} for a challenge.
 *
 * @param specJson null for a failed extraction
 * @param errors empty for a valid spec
 * @param userEdited corrected by the student: a new extraction must not replace it
 */
public record StoredSpec(
    int version,
    String sourceHash,
    String model,
    String specJson,
    List<String> errors,
    boolean userEdited) {

  public StoredSpec {
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  public boolean valid() {
    return specJson != null;
  }

  /** The stored spec as records; empty for a failed extraction. */
  public java.util.Optional<ChallengeSpec> spec() {
    if (specJson == null) {
      return java.util.Optional.empty();
    }
    try {
      return java.util.Optional.of(
          new com.fasterxml.jackson.databind.ObjectMapper()
              .readValue(specJson, ChallengeSpec.class));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Stored spec does not map to ChallengeSpec", e);
    }
  }
}
