package edu.uoc.planner.canvas.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;

/** {@code GET /api/v1/courses/{id}/tabs}. External tools (LTI) have {@code type == "external"}. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CanvasTab(
    @NotNull String id,
    @NotNull String label,
    @NotNull String type,
    Integer position,
    String visibility,
    String htmlUrl) {}
