package edu.uoc.planner.canvas.internal;

import edu.uoc.planner.canvas.Challenge;
import edu.uoc.planner.canvas.ChallengePage;
import edu.uoc.planner.canvas.Course;
import edu.uoc.planner.canvas.Deliverable;
import edu.uoc.planner.canvas.OtherPage;
import edu.uoc.planner.canvas.Statement;
import edu.uoc.planner.canvas.StudyGuide;
import edu.uoc.planner.canvas.internal.api.CanvasAssignment;
import edu.uoc.planner.canvas.internal.api.CanvasCourse;
import edu.uoc.planner.canvas.internal.api.CanvasModule;
import edu.uoc.planner.canvas.internal.api.CanvasModuleItem;
import edu.uoc.planner.canvas.internal.api.CanvasSubmission;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Canvas API records to the planner domain. Rules: docs/design.md, section 5. */
final class CanvasNormalizer {

  private static final String CHALLENGE_PREFIX = "repte";
  private static final String STATEMENT_PREFIX = "enunciat-de-lactivitat";

  /** English classrooms: each "CA" module opens with a "caN-…-guidelines" page. */
  private static final String STATEMENT_SUFFIX_EN = "-guidelines";

  private static final String STUDY_GUIDE_PREFIX = "ges-";
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  /**
   * Keeps the courses of the current term: the most common {@code enrollment_term_id} (the Tutoria
   * uses term 1 and is left out). On a tie, the highest term id wins.
   */
  List<Course> courses(List<CanvasCourse> courses) {
    Map<Long, Long> perTerm =
        courses.stream()
            .map(CanvasCourse::enrollmentTermId)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    if (perTerm.isEmpty()) {
      return List.of();
    }
    long currentTerm =
        perTerm.entrySet().stream()
            .max(Map.Entry.<Long, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
            .orElseThrow()
            .getKey();
    return courses.stream()
        .filter(c -> Objects.equals(c.enrollmentTermId(), currentTerm))
        .map(
            c ->
                new Course(
                    c.id(), c.courseCode(), c.name().strip(), currentTerm, c.uuid(), c.startAt()))
        .toList();
  }

  /**
   * Challenges are the modules whose name starts with "Repte". Courses without any fall back to the
   * modules that hold a deliverable with a due date.
   *
   * @param assignments every assignment of the course, also those outside challenges: the weight is
   *     relative to the whole course
   */
  List<Challenge> challenges(
      long courseId, List<CanvasModule> modules, List<CanvasAssignment> assignments) {
    Map<Long, CanvasAssignment> byId =
        assignments.stream()
            .collect(Collectors.toMap(CanvasAssignment::id, Function.identity(), (a, b) -> a));
    BigDecimal coursePoints =
        assignments.stream()
            .filter(CanvasNormalizer::countsForGrade)
            .map(CanvasNormalizer::points)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    List<CanvasModule> challengeModules =
        modules.stream().filter(CanvasNormalizer::isNamedRepte).toList();
    if (challengeModules.isEmpty()) {
      challengeModules = modules.stream().filter(m -> hasDueDeliverable(m, byId)).toList();
    }
    return challengeModules.stream()
        .sorted(
            Comparator.comparing(CanvasModule::position, Comparator.nullsLast(Integer::compare)))
        .map(m -> challenge(courseId, m, byId, coursePoints))
        .toList();
  }

  private Challenge challenge(
      long courseId,
      CanvasModule module,
      Map<Long, CanvasAssignment> assignments,
      BigDecimal coursePoints) {
    List<ChallengePage> pages = new ArrayList<>();
    List<Deliverable> deliverables = new ArrayList<>();
    List<Long> unresolved = new ArrayList<>();
    for (CanvasModuleItem item : items(module)) {
      switch (item.type()) {
        case "Page" -> pages.add(page(item));
        case "Assignment" -> {
          CanvasAssignment assignment = assignments.get(item.contentId());
          if (assignment == null) {
            unresolved.add(item.contentId());
          } else {
            deliverables.add(deliverable(assignment));
          }
        }
        default -> {
          // Discussions, files and external links are not deliverables.
        }
      }
    }
    BigDecimal points =
        deliverables.stream()
            .filter(Deliverable::countsForGrade)
            .map(Deliverable::points)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new Challenge(
        module.id(),
        courseId,
        module.position() == null ? 0 : module.position(),
        module.name().strip(),
        pages,
        deliverables,
        weight(points, coursePoints),
        unresolved);
  }

  /** Module items in order, each assignment once (some modules link the same one twice). */
  private static List<CanvasModuleItem> items(CanvasModule module) {
    if (module.items() == null) {
      return List.of();
    }
    Set<Long> seenAssignments = new LinkedHashSet<>();
    return module.items().stream()
        .sorted(
            Comparator.comparing(
                CanvasModuleItem::position, Comparator.nullsLast(Integer::compare)))
        .filter(i -> !"Assignment".equals(i.type()) || i.contentId() != null)
        .filter(i -> !"Assignment".equals(i.type()) || seenAssignments.add(i.contentId()))
        .filter(i -> !"Page".equals(i.type()) || i.pageUrl() != null)
        .toList();
  }

  private static ChallengePage page(CanvasModuleItem item) {
    String url = item.pageUrl();
    String title = item.title().strip();
    if (url.startsWith(STATEMENT_PREFIX) || url.endsWith(STATEMENT_SUFFIX_EN)) {
      return new Statement(url, title);
    }
    if (url.startsWith(STUDY_GUIDE_PREFIX)) {
      return new StudyGuide(url, title);
    }
    return new OtherPage(url, title);
  }

  private static Deliverable deliverable(CanvasAssignment a) {
    return new Deliverable(
        a.id(),
        a.name().strip(),
        a.unlockAt(),
        a.dueAt(),
        a.lockAt(),
        points(a),
        countsForGrade(a),
        a.submissionTypes() == null ? List.of() : a.submissionTypes(),
        submitted(a.submission()));
  }

  private static boolean submitted(CanvasSubmission s) {
    if (s == null) {
      return false;
    }
    return Boolean.TRUE.equals(s.excused())
        || s.submittedAt() != null
        || "submitted".equals(s.workflowState())
        || "pending_review".equals(s.workflowState())
        || "graded".equals(s.workflowState());
  }

  private static BigDecimal weight(BigDecimal points, BigDecimal coursePoints) {
    if (coursePoints.signum() == 0) {
      return BigDecimal.ZERO;
    }
    return points.multiply(HUNDRED).divide(coursePoints, 2, RoundingMode.HALF_UP);
  }

  private static boolean isNamedRepte(CanvasModule module) {
    return module.name().strip().toLowerCase(Locale.ROOT).startsWith(CHALLENGE_PREFIX);
  }

  private static boolean hasDueDeliverable(
      CanvasModule module, Map<Long, CanvasAssignment> assignments) {
    return items(module).stream()
        .filter(i -> "Assignment".equals(i.type()))
        .map(i -> assignments.get(i.contentId()))
        .anyMatch(a -> a != null && a.dueAt() != null);
  }

  private static boolean countsForGrade(CanvasAssignment a) {
    return !Boolean.TRUE.equals(a.omitFromFinalGrade());
  }

  private static BigDecimal points(CanvasAssignment a) {
    return a.pointsPossible() == null ? BigDecimal.ZERO : a.pointsPossible();
  }
}
