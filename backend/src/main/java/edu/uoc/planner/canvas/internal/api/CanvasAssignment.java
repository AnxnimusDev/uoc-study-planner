package edu.uoc.planner.canvas.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/v1/courses/{id}/assignments?include[]=submission}. Quizzes have {@code
 * submissionTypes == ["external_tool"]} and no readable content.
 *
 * @param submission the student's own; null when Canvas does not send it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CanvasAssignment(
    @NotNull Long id,
    Long courseId,
    @NotNull String name,
    String description,
    Integer position,
    BigDecimal pointsPossible,
    String gradingType,
    Instant unlockAt,
    Instant dueAt,
    Instant lockAt,
    Instant updatedAt,
    Boolean omitFromFinalGrade,
    List<String> submissionTypes,
    Long assignmentGroupId,
    Boolean published,
    String htmlUrl,
    CanvasSubmission submission) {}
