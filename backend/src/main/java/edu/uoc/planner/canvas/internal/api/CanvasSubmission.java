package edu.uoc.planner.canvas.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;

/**
 * The student's own submission, from {@code include[]=submission}. Only its state is read; content,
 * grade and comments are ignored.
 *
 * @param workflowState {@code unsubmitted}, {@code submitted}, {@code pending_review} or {@code
 *     graded}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CanvasSubmission(String workflowState, Instant submittedAt, Boolean excused) {}
