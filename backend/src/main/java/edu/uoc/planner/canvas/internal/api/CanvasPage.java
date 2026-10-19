package edu.uoc.planner.canvas.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** {@code GET /api/v1/courses/{id}/pages/{page_url}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CanvasPage(
    @NotNull Long pageId,
    @NotNull String url,
    @NotNull String title,
    String body,
    Instant createdAt,
    Instant updatedAt,
    Instant publishAt,
    Boolean published,
    Boolean lockedForUser,
    String htmlUrl) {}
