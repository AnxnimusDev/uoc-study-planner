package edu.uoc.planner.extraction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Requirements extracted from a challenge statement: {@code docs/challenge-spec.schema.json} v1.
 * Only built from JSON that already passed validation. Never holds solutions (ADR 0003).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChallengeSpec(
    int schemaVersion,
    String challengeTitle,
    String language,
    String summary,
    List<Deliverable> deliverables,
    List<Task> tasks,
    AiPolicy aiPolicy,
    List<AssessmentCriterion> assessmentCriteria,
    List<ExternalResource> externalResources,
    List<String> warnings,
    double confidence) {

  public ChallengeSpec {
    deliverables = copy(deliverables);
    tasks = copy(tasks);
    assessmentCriteria = copy(assessmentCriteria);
    externalResources = copy(externalResources);
    warnings = copy(warnings);
  }

  /**
   * @param ref {@code assignment:<id>} or {@code discussion:<id>}
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Deliverable(
      String ref, DeliverableType type, Format format, List<String> formatRequirements) {

    public Deliverable {
      formatRequirements = copy(formatRequirements);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Format(String fileType, Boolean compressed, Integer maxPages, Double maxMinutes) {}

  /**
   * @param estimatedHours one study session: more than 0 and at most 3
   * @param minDaysAfterDependency e.g. forum replies wait at least 2 days after the own post
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Task(
      String id,
      String title,
      Integer quantity,
      String unit,
      double estimatedHours,
      String estimationBasis,
      String deliverableRef,
      List<String> dependsOn,
      Integer minDaysAfterDependency,
      TaskKind kind) {

    public Task {
      dependsOn = copy(dependsOn);
    }
  }

  /**
   * @param evidence the literal sentence of the statement that sets the policy
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AiPolicy(
      AiPolicyLevel level, List<String> allowedUses, Boolean citationRequired, String evidence) {

    public AiPolicy {
      allowedUses = copy(allowedUses);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AssessmentCriterion(String criterion, Double weightPct, CriterionSource source) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ExternalResource(String title, String url, ResourceKind kind) {}

  public enum DeliverableType {
    DOCUMENT,
    QUIZ,
    CODE,
    VIDEO,
    FORUM,
    PROTOTYPE,
    OTHER
  }

  public enum TaskKind {
    WORK,
    READING,
    QUIZ,
    FORUM_POST,
    FORUM_REPLY,
    VIDEO_RECORDING,
    REVIEW,
    SUBMISSION
  }

  public enum AiPolicyLevel {
    FORBIDDEN,
    LIMITED,
    ALLOWED,
    NOT_STATED
  }

  public enum CriterionSource {
    STATEMENT,
    RUBRIC,
    STUDY_GUIDE
  }

  public enum ResourceKind {
    CANVAS_FILE,
    UOC_PUBLIC,
    EXTERNAL,
    STALE_LINK
  }

  private static <T> List<T> copy(List<T> list) {
    return list == null ? List.of() : List.copyOf(list);
  }
}
