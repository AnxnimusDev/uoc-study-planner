package edu.uoc.planner.planning;

import java.time.Duration;
import java.time.Instant;

/**
 * Work that does not fit before its deadline. Never trimmed silently: the challenge is flagged
 * {@code AT_RISK} and the missing time is reported.
 *
 * @param title shown to the student with the course and deadline (ADR 0011)
 * @param dueAt real deadline; null when there is none
 * @param reason in Spanish, shown to the student
 */
public record AtRiskTask(
    String taskId,
    long challengeId,
    String courseCode,
    String title,
    Instant dueAt,
    Duration missing,
    String reason) {}
