package edu.uoc.planner.canvas.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/v1/courses/{id}/modules?include[]=items}. A challenge is a module named "Repte…".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CanvasModule(
    @NotNull Long id,
    @NotNull String name,
    Integer position,
    Instant unlockAt,
    String state,
    Integer itemsCount,
    List<@Valid CanvasModuleItem> items) {}
