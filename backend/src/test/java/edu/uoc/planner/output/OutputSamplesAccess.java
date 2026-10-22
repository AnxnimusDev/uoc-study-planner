package edu.uoc.planner.output;

import edu.uoc.planner.planning.PlannedSession;
import java.util.List;

/** Lets tests in {@code output.internal} reuse the sample week. */
public final class OutputSamplesAccess {

  private OutputSamplesAccess() {}

  public static List<PlannedSession> sessions() {
    return OutputSamples.sessions();
  }
}
