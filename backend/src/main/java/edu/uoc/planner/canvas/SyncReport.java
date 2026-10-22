package edu.uoc.planner.canvas;

import java.util.List;

/** What one Canvas sync stored, per selected course of the current term. */
public record SyncReport(List<CourseSync> courses) {

  public SyncReport {
    courses = List.copyOf(courses);
  }

  /**
   * @param pagesChanged pages stored for the first time or whose body changed: their challenge
   *     needs a new extraction
   * @param missingPages linked from a challenge module but not readable (404)
   * @param unresolvedAssignments linked from a module but not found in Canvas
   */
  public record CourseSync(
      long courseId,
      String name,
      int challenges,
      int deliverables,
      int pagesStored,
      int pagesChanged,
      List<String> missingPages,
      List<Long> unresolvedAssignments) {

    public CourseSync {
      missingPages = List.copyOf(missingPages);
      unresolvedAssignments = List.copyOf(unresolvedAssignments);
    }
  }
}
