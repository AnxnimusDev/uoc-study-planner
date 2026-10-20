package edu.uoc.planner.canvas.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Item of a module. Deliverables are resolved through {@code contentId}, never through links in the
 * page HTML.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CanvasModuleItem(
    @NotNull Long id,
    Long moduleId,
    Integer position,
    @NotNull String title,
    @NotNull String type,
    Long contentId,
    String pageUrl,
    String htmlUrl,
    String url,
    Instant publishAt) {}
