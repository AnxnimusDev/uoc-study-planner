package edu.uoc.planner.canvas.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** {@code GET /api/v1/courses}. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CanvasCourse(
    @NotNull Long id,
    @NotNull String name,
    String courseCode,
    Long enrollmentTermId,
    String uuid,
    String workflowState,
    String locale,
    String timeZone,
    Instant startAt,
    Instant endAt) {}
