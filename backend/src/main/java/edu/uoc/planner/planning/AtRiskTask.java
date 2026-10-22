package edu.uoc.planner.planning;

import java.time.Duration;

/**
 * Work that does not fit before its deadline. Never trimmed silently: the challenge is flagged
 * {@code AT_RISK} and the missing time is reported.
 *
 * @param reason in Spanish, shown to the student
 */
public record AtRiskTask(String taskId, long challengeId, Duration missing, String reason) {}
