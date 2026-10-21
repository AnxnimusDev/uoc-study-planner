package edu.uoc.planner.canvas;

import java.time.Instant;

/** Published when Canvas answers 401. Carries no cookie nor response data (ADR 0005). */
public record CanvasSessionExpiredEvent(Instant detectedAt) {}
