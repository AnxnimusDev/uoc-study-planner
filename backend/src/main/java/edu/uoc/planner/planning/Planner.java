package edu.uoc.planner.planning;

/** Tasks + free slots + deadlines → plan. Greedy in the MVP, Timefold in phase 2 (ADR 0004). */
public interface Planner {

  Plan plan(PlanRequest request);
}
