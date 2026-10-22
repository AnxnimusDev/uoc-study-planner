package edu.uoc.planner.canvas.internal;

import edu.uoc.planner.canvas.CanvasUnexpectedResponseException;
import edu.uoc.planner.canvas.Challenge;
import edu.uoc.planner.canvas.ChallengePage;
import edu.uoc.planner.canvas.Course;
import edu.uoc.planner.canvas.Deliverable;
import edu.uoc.planner.canvas.OtherPage;
import edu.uoc.planner.canvas.Statement;
import edu.uoc.planner.canvas.StudyGuide;
import edu.uoc.planner.canvas.SyncReport;
import edu.uoc.planner.canvas.SyncReport.CourseSync;
import edu.uoc.planner.canvas.internal.CanvasStore.PageRole;
import edu.uoc.planner.canvas.internal.api.CanvasAssignment;
import edu.uoc.planner.canvas.internal.api.CanvasModule;
import edu.uoc.planner.canvas.internal.api.CanvasPage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reads everything a course needs from Canvas first, then writes it in one transaction per course,
 * so no transaction waits on the network and a failed course leaves the others intact.
 */
@Component
public class CanvasMirror {

  private static final Logger log = LoggerFactory.getLogger(CanvasMirror.class);
  private static final String APPROACH_PREFIX = "plantejament";

  private final CanvasClient client;
  private final CanvasStore store;
  private final CanvasProperties properties;
  private final TransactionTemplate transactions;
  private final Clock clock;
  private final CanvasNormalizer normalizer = new CanvasNormalizer();

  CanvasMirror(
      CanvasClient client,
      CanvasStore store,
      CanvasProperties properties,
      TransactionTemplate transactions,
      Clock clock) {
    this.client = client;
    this.store = store;
    this.properties = properties;
    this.transactions = transactions;
    this.clock = clock;
  }

  public SyncReport sync() {
    client.self();
    List<Course> courses = normalizer.courses(client.courses());
    transactions.executeWithoutResult(tx -> courses.forEach(c -> store.upsertCourse(c, icsUrl(c))));
    Set<Long> selected = store.selectedCourses();
    List<CourseSync> report = new ArrayList<>();
    for (Course course : courses) {
      if (selected.contains(course.canvasId())) {
        report.add(syncCourse(course));
      }
    }
    return new SyncReport(report);
  }

  private CourseSync syncCourse(Course course) {
    long courseId = course.canvasId();
    List<CanvasModule> modules = client.modules(courseId);
    List<CanvasAssignment> assignments = new ArrayList<>(client.assignments(courseId));
    List<Challenge> challenges = normalizer.challenges(courseId, modules, assignments);

    Set<Long> unresolved = new LinkedHashSet<>();
    for (Challenge challenge : challenges) {
      for (long id : challenge.unresolvedAssignmentIds()) {
        fetchAssignment(courseId, id).ifPresentOrElse(assignments::add, () -> unresolved.add(id));
      }
    }
    if (challenges.stream().anyMatch(c -> !c.unresolvedAssignmentIds().isEmpty())) {
      challenges = normalizer.challenges(courseId, modules, assignments);
    }

    List<FetchedPage> pages = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    for (Challenge challenge : challenges) {
      for (ChallengePage page : challenge.pages()) {
        role(page)
            .ifPresent(
                role ->
                    fetchPage(courseId, page.pageUrl())
                        .ifPresentOrElse(
                            p -> pages.add(new FetchedPage(challenge.canvasModuleId(), role, p)),
                            () -> missing.add(page.pageUrl())));
      }
    }

    List<Challenge> toStore = challenges;
    int[] counts = new int[3]; // deliverables, pages stored, pages changed
    transactions.executeWithoutResult(
        tx -> {
          for (Challenge challenge : toStore) {
            long challengeId = store.upsertChallenge(challenge);
            for (Deliverable deliverable : challenge.deliverables()) {
              store.upsertDeliverable(challengeId, deliverable);
              counts[0]++;
            }
            for (FetchedPage fetched : pages) {
              if (fetched.moduleId() == challenge.canvasModuleId()) {
                CanvasPage p = fetched.page();
                String body = p.body() == null ? "" : p.body();
                boolean changed =
                    store.upsertPage(
                        courseId,
                        challengeId,
                        p.url(),
                        p.title(),
                        fetched.role(),
                        body,
                        sha256(body),
                        p.updatedAt() == null ? clock.instant() : p.updatedAt());
                counts[1]++;
                counts[2] += changed ? 1 : 0;
              }
            }
          }
        });
    log.info(
        "Synced course {}: {} challenges, {} deliverables, {} pages ({} changed)",
        courseId,
        toStore.size(),
        counts[0],
        counts[1],
        counts[2]);
    return new CourseSync(
        courseId,
        course.name(),
        toStore.size(),
        counts[0],
        counts[1],
        counts[2],
        missing,
        List.copyOf(unresolved));
  }

  /** Only the pages the extractor reads (ADR 0003): statement, study guide and approach. */
  private static Optional<PageRole> role(ChallengePage page) {
    return switch (page) {
      case Statement s -> Optional.of(PageRole.STATEMENT);
      case StudyGuide g -> Optional.of(PageRole.STUDY_GUIDE);
      case OtherPage o ->
          o.pageUrl().startsWith(APPROACH_PREFIX)
              ? Optional.of(PageRole.APPROACH)
              : Optional.empty();
    };
  }

  private Optional<CanvasPage> fetchPage(long courseId, String pageUrl) {
    try {
      return Optional.of(client.page(courseId, pageUrl));
    } catch (CanvasUnexpectedResponseException e) {
      log.warn("Page {} of course {} not readable: {}", pageUrl, courseId, e.getMessage());
      return Optional.empty();
    }
  }

  private Optional<CanvasAssignment> fetchAssignment(long courseId, long assignmentId) {
    try {
      return Optional.of(client.assignment(courseId, assignmentId));
    } catch (CanvasUnexpectedResponseException e) {
      log.warn(
          "Assignment {} of course {} not readable: {}", assignmentId, courseId, e.getMessage());
      return Optional.empty();
    }
  }

  /** Public calendar of the course, readable without a session (docs/canvas-api-notes.md). */
  private String icsUrl(Course course) {
    if (course.uuid() == null) {
      return null;
    }
    return properties.baseUrl().toString().replaceAll("/+$", "")
        + "/feeds/calendars/course_"
        + course.uuid()
        + ".ics";
  }

  private static String sha256(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private record FetchedPage(long moduleId, PageRole role, CanvasPage page) {}
}
