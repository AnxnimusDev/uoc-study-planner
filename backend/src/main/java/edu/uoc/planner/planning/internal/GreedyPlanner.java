package edu.uoc.planner.planning.internal;

import edu.uoc.planner.extraction.ChallengeSpec.TaskKind;
import edu.uoc.planner.planning.AtRiskTask;
import edu.uoc.planner.planning.Notice;
import edu.uoc.planner.planning.Plan;
import edu.uoc.planner.planning.PlanLimits;
import edu.uoc.planner.planning.PlanRequest;
import edu.uoc.planner.planning.PlanTask;
import edu.uoc.planner.planning.PlannedSession;
import edu.uoc.planner.planning.Planner;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

/**
 * MVP planner (docs/design.md, section 8). Tasks are taken in dependency order, soonest target
 * first (recommended date, then effective deadline, weight, challenge, order), and placed in the
 * earliest free time that respects the hard limits. Soft preferences are tried first and relaxed
 * one by one. Whatever does not fit stays partly planned and is reported at risk.
 */
public final class GreedyPlanner implements Planner {

  /** Challenges worth this much or more get a one-hour review instead of half an hour. */
  private static final double LONG_REVIEW_WEIGHT_PCT = 20;

  private static final Set<TaskKind> FINAL_KINDS = Set.of(TaskKind.QUIZ, TaskKind.SUBMISSION);

  private record Tier(Instant latest, boolean preferredOnly, boolean courseLimit) {}

  @Override
  public Plan plan(PlanRequest request) {
    Instant horizonEnd = request.now().plus(Duration.ofDays(7L * request.horizonWeeks()));
    Capacity capacity = Capacity.of(request.slots(), request.now(), horizonEnd, request.zone());
    List<PlannedSession> sessions = new ArrayList<>();
    Map<String, Duration> keptTime = new HashMap<>();
    Map<String, Instant> keptEnd = new HashMap<>();
    for (PlannedSession kept : request.keptSessions()) {
      if (!fits(capacity, kept, request.limits())) {
        continue;
      }
      capacity.reserve(kept.start(), kept.end(), kept.courseCode());
      sessions.add(kept);
      keptTime.merge(kept.taskId(), kept.duration(), Duration::plus);
      keptEnd.merge(kept.taskId(), kept.end(), (a, b) -> a.isAfter(b) ? a : b);
    }

    List<PlanTask> tasks = withReviews(request.tasks(), request.limits());
    Map<String, PlanTask> byId = new LinkedHashMap<>();
    tasks.forEach(t -> byId.put(t.id(), t));

    Map<String, Instant> finished = new HashMap<>();
    Set<String> satisfied = new HashSet<>();
    Set<String> blocked = new HashSet<>();
    Set<String> deferred = new HashSet<>();
    List<AtRiskTask> atRisk = new ArrayList<>();
    List<Notice> notices = new ArrayList<>();
    List<String> deferredOrder = new ArrayList<>();

    for (PlanTask task : dependencyOrder(tasks, byId, request)) {
      if (task.kind() == TaskKind.VIDEO_RECORDING) {
        notices.add(
            new Notice(
                task.id(),
                Notice.Type.VIDEO_WINDOW,
                task.opensAt() == null ? request.now() : task.opensAt(),
                task.dueAt(),
                "Graba el vídeo dentro de la ventana: el profesorado lo convoca y hay poco tiempo"
                    + " para subirlo."));
        satisfied.add(task.id());
        continue;
      }
      Optional<String> blocker = task.dependsOn().stream().filter(blocked::contains).findFirst();
      Optional<String> deferredDep =
          task.dependsOn().stream().filter(deferred::contains).findFirst();
      if (task.dueAt() != null && !task.dueAt().isAfter(request.now())) {
        risk(atRisk, blocked, task, remaining(task, keptTime), "Plazo vencido");
        continue;
      }
      if (blocker.isPresent()) {
        risk(
            atRisk,
            blocked,
            task,
            remaining(task, keptTime),
            "Depende de " + blocker.get() + ", que no se ha podido planificar");
        continue;
      }
      if (deferredDep.isPresent()) {
        deferred.add(task.id());
        deferredOrder.add(task.id());
        continue;
      }

      Instant earliest = earliest(task, request.now(), finished, byId);
      if (!earliest.isBefore(horizonEnd)) {
        deferred.add(task.id());
        deferredOrder.add(task.id());
        continue;
      }
      Instant deadline = effectiveDeadline(task, request);
      if (deadline != null && !deadline.isAfter(earliest) && task.dueAt().isAfter(earliest)) {
        notices.add(
            new Notice(
                task.id(),
                Notice.Type.MARGIN_SKIPPED,
                earliest,
                task.dueAt(),
                "No queda margen antes del plazo: se planifica hasta la hora de entrega."));
        deadline = task.dueAt();
      }
      boolean beyondHorizon = deadline == null || deadline.isAfter(horizonEnd);
      Instant latest = beyondHorizon ? horizonEnd : deadline;

      long remaining = remaining(task, keptTime).toMinutes();
      List<PlannedSession> placed = new ArrayList<>();
      if (remaining > 0) {
        for (Tier tier : tiers(task, latest)) {
          Capacity attempt = capacity.copy();
          List<PlannedSession> attemptSessions = new ArrayList<>();
          long left =
              place(attempt, task, earliest, tier, remaining, request.limits(), attemptSessions);
          boolean lastTier = tier.equals(tiers(task, latest).getLast());
          if (left == 0 || lastTier) {
            capacity = attempt;
            placed = attemptSessions;
            remaining = left;
            break;
          }
        }
      }
      sessions.addAll(placed);
      if (remaining == 0) {
        Instant end = placed.isEmpty() ? keptEnd.get(task.id()) : placed.getLast().end();
        Instant kept = keptEnd.get(task.id());
        finished.put(task.id(), kept != null && (end == null || kept.isAfter(end)) ? kept : end);
      } else if (beyondHorizon) {
        deferred.add(task.id());
        deferredOrder.add(task.id());
      } else {
        risk(atRisk, blocked, task, Duration.ofMinutes(remaining), "No cabe antes del plazo");
      }
    }

    sessions.sort(
        Comparator.comparing(PlannedSession::start).thenComparing(PlannedSession::taskId));
    Duration unscheduled =
        atRisk.stream().map(AtRiskTask::missing).reduce(Duration.ZERO, Duration::plus);
    List<Long> riskyChallenges =
        new ArrayList<>(
            new TreeSet<>(
                atRisk.stream()
                    .map(AtRiskTask::challengeId)
                    // challengeId 0: time with no challenge (ADR 0007), never flags one.
                    .filter(id -> id != 0)
                    .toList()));
    return new Plan(sessions, atRisk, notices, deferredOrder, riskyChallenges, unscheduled);
  }

  /**
   * Places the remaining minutes, whole sessions first: a session is only cut when no gap before
   * the deadline can hold it. Tails shorter than the minimum session are avoided.
   *
   * @return minutes left unplaced
   */
  private static long place(
      Capacity capacity,
      PlanTask task,
      Instant earliest,
      Tier tier,
      long remaining,
      PlanLimits limits,
      List<PlannedSession> out) {
    long min = limits.minSessionMinutes();
    while (remaining > 0) {
      long left = remaining;
      long desired = desiredSession(remaining, limits);
      Optional<Capacity.Spot> spot =
          capacity.find(
              earliest,
              tier.latest(),
              desired,
              desired,
              tier.preferredOnly(),
              tier.courseLimit(),
              task.courseCode(),
              limits::maxDailyMinutes);
      if (spot.isEmpty()) {
        spot =
            capacity
                .find(
                    earliest,
                    tier.latest(),
                    Math.min(min, remaining),
                    desired,
                    tier.preferredOnly(),
                    tier.courseLimit(),
                    task.courseCode(),
                    limits::maxDailyMinutes)
                .map(s -> avoidShortTail(s, left, min));
      }
      if (spot.isEmpty() || spot.get().minutes() <= 0) {
        break;
      }
      Instant start = spot.get().start();
      Instant end = start.plus(Duration.ofMinutes(spot.get().minutes()));
      capacity.reserve(start, end, task.courseCode());
      out.add(
          new PlannedSession(
              task.id(),
              task.challengeId(),
              task.courseCode(),
              task.title(),
              task.kind(),
              start,
              end,
              false));
      remaining -= spot.get().minutes();
    }
    return remaining;
  }

  /** Up to the maximum session, leaving either nothing or at least a minimum session. */
  private static long desiredSession(long remaining, PlanLimits limits) {
    long max = limits.maxSessionMinutes();
    long min = limits.minSessionMinutes();
    if (remaining <= max) {
      return remaining;
    }
    long session = max;
    if (remaining - session < min) {
      session = Math.max(min, remaining - min);
    }
    return session;
  }

  private static Capacity.Spot avoidShortTail(Capacity.Spot spot, long remaining, long min) {
    long minutes = spot.minutes();
    long tail = remaining - minutes;
    if (tail > 0 && tail < min) {
      minutes = remaining - min;
    }
    return minutes >= Math.min(min, remaining)
        ? new Capacity.Spot(spot.start(), minutes)
        : new Capacity.Spot(spot.start(), 0);
  }

  /** Most preferred first; the last one only keeps the hard constraints. */
  private static List<Tier> tiers(PlanTask task, Instant latest) {
    List<Tier> tiers = new ArrayList<>();
    if (task.recommendedBy() != null && task.recommendedBy().isBefore(latest)) {
      tiers.add(new Tier(task.recommendedBy(), true, true));
      tiers.add(new Tier(task.recommendedBy(), false, true));
    }
    tiers.add(new Tier(latest, true, true));
    tiers.add(new Tier(latest, false, true));
    tiers.add(new Tier(latest, false, false));
    return tiers;
  }

  private static Instant earliest(
      PlanTask task, Instant now, Map<String, Instant> finished, Map<String, PlanTask> byId) {
    Instant earliest = task.opensAt() != null && task.opensAt().isAfter(now) ? task.opensAt() : now;
    for (String dependency : task.dependsOn()) {
      Instant end = finished.get(dependency);
      if (end != null) {
        Instant ready = end.plus(Duration.ofDays(task.minDaysAfterDependency()));
        if (ready.isAfter(earliest)) {
          earliest = ready;
        }
      }
    }
    return earliest;
  }

  private static Instant effectiveDeadline(PlanTask task, PlanRequest request) {
    if (task.dueAt() == null) {
      return null;
    }
    return task.dueAt().atZone(request.zone()).minusDays(task.marginDays()).toInstant();
  }

  /**
   * A kept session stays only if it is still free time within its day's limit: availability may
   * have changed since the previous plan. Otherwise its time is planned again.
   */
  private static boolean fits(Capacity capacity, PlannedSession kept, PlanLimits limits) {
    long minutes = kept.duration().toMinutes();
    return capacity
        .find(
            kept.start(),
            kept.end(),
            minutes,
            minutes,
            false,
            false,
            kept.courseCode(),
            limits::maxDailyMinutes)
        .filter(spot -> spot.start().equals(kept.start()) && spot.minutes() == minutes)
        .isPresent();
  }

  private static Duration remaining(PlanTask task, Map<String, Duration> keptTime) {
    Duration left = task.estimate().minus(keptTime.getOrDefault(task.id(), Duration.ZERO));
    return left.isNegative() ? Duration.ZERO : left;
  }

  private static void risk(
      List<AtRiskTask> atRisk,
      Set<String> blocked,
      PlanTask task,
      Duration missing,
      String reason) {
    atRisk.add(new AtRiskTask(task.id(), task.challengeId(), missing, reason));
    blocked.add(task.id());
  }

  /**
   * Kahn's algorithm with a priority queue: among the tasks whose dependencies are done, the
   * soonest target goes first. Tasks in a cycle come last, in priority order.
   */
  private static List<PlanTask> dependencyOrder(
      List<PlanTask> tasks, Map<String, PlanTask> byId, PlanRequest request) {
    Comparator<PlanTask> priority = priority(request);
    Map<String, Integer> pending = new HashMap<>();
    Map<String, List<String>> dependents = new HashMap<>();
    for (PlanTask task : tasks) {
      int count = 0;
      for (String dependency : new HashSet<>(task.dependsOn())) {
        if (byId.containsKey(dependency) && !dependency.equals(task.id())) {
          count++;
          dependents.computeIfAbsent(dependency, d -> new ArrayList<>()).add(task.id());
        }
      }
      pending.put(task.id(), count);
    }
    PriorityQueue<PlanTask> ready = new PriorityQueue<>(priority);
    tasks.stream().filter(t -> pending.get(t.id()) == 0).forEach(ready::add);
    List<PlanTask> order = new ArrayList<>();
    while (!ready.isEmpty()) {
      PlanTask next = ready.poll();
      order.add(next);
      for (String dependent : dependents.getOrDefault(next.id(), List.of())) {
        if (pending.merge(dependent, -1, Integer::sum) == 0) {
          ready.add(byId.get(dependent));
        }
      }
    }
    tasks.stream().filter(t -> !order.contains(t)).sorted(priority).forEach(order::add);
    return order;
  }

  private static Comparator<PlanTask> priority(PlanRequest request) {
    Comparator<Instant> nullsLast = Comparator.nullsLast(Comparator.naturalOrder());
    return Comparator.<PlanTask, Instant>comparing(t -> target(t, request), nullsLast)
        .thenComparing(t -> effectiveDeadline(t, request), nullsLast)
        .thenComparing(PlanTask::challengeWeightPct, Comparator.reverseOrder())
        .thenComparingLong(PlanTask::challengeId)
        .thenComparingInt(PlanTask::order)
        .thenComparing(PlanTask::id);
  }

  private static Instant target(PlanTask task, PlanRequest request) {
    Instant deadline = effectiveDeadline(task, request);
    if (task.recommendedBy() == null) {
      return deadline;
    }
    return deadline == null || task.recommendedBy().isBefore(deadline)
        ? task.recommendedBy()
        : deadline;
  }

  /**
   * Adds a review before each graded deliverable (design: "hueco de revisión antes de cada entrega
   * con nota"): after the work on it, before the quiz or submission.
   */
  private static List<PlanTask> withReviews(List<PlanTask> tasks, PlanLimits limits) {
    Map<String, List<PlanTask>> byDeliverable = new LinkedHashMap<>();
    for (PlanTask task : tasks) {
      if (task.graded()
          && task.deliverableRef() != null
          && task.kind() != TaskKind.VIDEO_RECORDING) {
        byDeliverable
            .computeIfAbsent(
                task.challengeId() + "|" + task.deliverableRef(), k -> new ArrayList<>())
            .add(task);
      }
    }
    Map<String, PlanTask> result = new LinkedHashMap<>();
    tasks.forEach(t -> result.put(t.id(), t));
    for (List<PlanTask> group : byDeliverable.values()) {
      if (group.stream().anyMatch(t -> t.kind() == TaskKind.REVIEW)) {
        continue;
      }
      List<PlanTask> finals = group.stream().filter(t -> FINAL_KINDS.contains(t.kind())).toList();
      List<String> before =
          group.stream().filter(t -> !FINAL_KINDS.contains(t.kind())).map(PlanTask::id).toList();
      PlanTask first = group.getFirst();
      long minutes =
          Math.max(
              first.challengeWeightPct() >= LONG_REVIEW_WEIGHT_PCT ? 60 : 30,
              limits.minSessionMinutes());
      String id = "review:" + first.challengeId() + ":" + first.deliverableRef();
      PlanTask anchor = finals.isEmpty() ? first : finals.getFirst();
      Instant recommended =
          finals.stream()
              .map(PlanTask::recommendedBy)
              .filter(r -> r != null)
              .min(Comparator.naturalOrder())
              .orElse(null);
      PlanTask review =
          new PlanTask(
              id,
              first.challengeId(),
              first.courseCode(),
              first.challengeWeightPct(),
              anchor.order(),
              "Repaso antes de: " + anchor.title(),
              TaskKind.REVIEW,
              Duration.ofMinutes(minutes),
              null,
              anchor.dueAt(),
              anchor.marginDays(),
              recommended,
              before,
              0,
              first.deliverableRef(),
              true);
      result.put(id, review);
      finals.forEach(f -> result.put(f.id(), result.get(f.id()).withDependency(id)));
    }
    return new ArrayList<>(result.values());
  }
}
