package edu.uoc.planner.output;

import java.time.Instant;

/** A real deadline ({@code due_at}) for the deliverables calendar. */
public record DeliverableDeadline(
    long canvasAssignmentId, String title, String courseCode, Instant dueAt) {}
