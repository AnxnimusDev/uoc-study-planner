package edu.uoc.planner.canvas.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * {@code GET /api/v1/courses/{id}/assignment_groups}. {@code groupWeight} is not a challenge
 * weight.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CanvasAssignmentGroup(
    @NotNull Long id, @NotNull String name, Integer position, BigDecimal groupWeight) {}
