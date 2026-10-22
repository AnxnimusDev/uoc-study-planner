package edu.uoc.planner.scheduling;

import edu.uoc.planner.canvas.SyncReport;

/**
 * One full update: Canvas, extraction and plan.
 *
 * @param sync null when Canvas could not be read; then {@code syncProblem} says why and the plan
 *     uses the data already stored
 */
public record PipelineReport(
    SyncReport sync, String syncProblem, ExtractionReport extraction, PlanReport plan) {}
