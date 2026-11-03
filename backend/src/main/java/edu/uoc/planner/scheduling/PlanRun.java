package edu.uoc.planner.scheduling;

import edu.uoc.planner.availability.AvailabilityProfile;
import edu.uoc.planner.availability.AvailabilityService;
import edu.uoc.planner.canvas.CanvasCatalog;
import edu.uoc.planner.canvas.ChallengeStatus;
import edu.uoc.planner.canvas.StoredChallenge;
import edu.uoc.planner.extraction.ChallengeSpec;
import edu.uoc.planner.extraction.SpecStore;
import edu.uoc.planner.extraction.StoredSpec;
import edu.uoc.planner.planning.Plan;
import edu.uoc.planner.planning.PlanLimits;
import edu.uoc.planner.planning.PlanRequest;
import edu.uoc.planner.planning.PlanStore;
import edu.uoc.planner.planning.PlanTask;
import edu.uoc.planner.planning.PlanTrigger;
import edu.uoc.planner.planning.Planner;
import edu.uoc.planner.scheduling.internal.PlanTaskAdapter;
import edu.uoc.planner.scheduling.internal.ProgressStore;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Flow steps 4–5 (docs/design.md, section 4): free slots for the next six weeks, tasks from the
 * stored challenges, plan, store it, and flag the challenges that do not fit as {@code AT_RISK}.
 */
@Service
public class PlanRun {

  private static final Logger log = LoggerFactory.getLogger(PlanRun.class);

  public static final int HORIZON_WEEKS = 6;

  /** Sessions this close are kept when replanning (design section 8). */
  static final Duration KEEP_WINDOW = Duration.ofDays(2);

  private final CanvasCatalog catalog;
  private final SpecStore specs;
  private final AvailabilityService availability;
  private final Planner planner;
  private final PlanStore plans;
  private final ProgressStore progress;

  PlanRun(
      CanvasCatalog catalog,
      SpecStore specs,
      AvailabilityService availability,
      Planner planner,
      PlanStore plans,
      ProgressStore progress) {
    this.catalog = catalog;
    this.specs = specs;
    this.availability = availability;
    this.planner = planner;
    this.plans = plans;
    this.progress = progress;
  }

  public PlanReport run(Instant now) {
    return run(now, PlanTrigger.MANUAL);
  }

  /**
   * @throws edu.uoc.planner.availability.AvailabilityConfigException without a usable profile
   */
  public PlanReport run(Instant now, PlanTrigger trigger) {
    AvailabilityProfile profile = availability.profile();
    LocalDate today = now.atZone(profile.zone()).toLocalDate();
    List<StoredChallenge> challenges = catalog.selectedChallenges();
    Map<Long, Optional<StoredSpec>> latest = new HashMap<>();
    Map<Long, ChallengeSpec> valid = new HashMap<>();
    for (StoredChallenge challenge : challenges) {
      Optional<StoredSpec> spec = specs.latest(challenge.id());
      latest.put(challenge.id(), spec);
      spec.flatMap(StoredSpec::spec).ifPresent(s -> valid.put(challenge.id(), s));
    }
    PlanTaskAdapter.Result input =
        PlanTaskAdapter.adapt(challenges, valid, profile, catalog.selectedCourseCodes(), now);
    List<String> reset = new ArrayList<>();
    Map<String, Duration> left = progress(challenges, latest, reset);
    Plan plan =
        planner.plan(
            new PlanRequest(
                now,
                profile.zone(),
                input.tasks(),
                availability.freeSlots(today, today.plusWeeks(HORIZON_WEEKS).plusDays(1)),
                PlanLimits.of(profile),
                HORIZON_WEEKS,
                plans.sessionsToKeep(now, KEEP_WINDOW),
                left));
    plans.save(plan, input.tasks(), trigger, now);

    Set<Long> risky = new HashSet<>(plan.atRiskChallengeIds());
    for (StoredChallenge challenge : challenges) {
      if (risky.contains(challenge.id())) {
        catalog.updateStatus(challenge.id(), ChallengeStatus.AT_RISK);
      } else if (challenge.status() == ChallengeStatus.AT_RISK) {
        catalog.updateStatus(challenge.id(), statusWithoutRisk(latest.get(challenge.id())));
      }
    }
    log.info(
        "Plan stored: {} sessions, {} tasks at risk, {} min unscheduled",
        plan.sessions().size(),
        plan.atRisk().size(),
        plan.unscheduled().toMinutes());
    return new PlanReport(
        plan, input.unestimated(), input.unknownWeeklyCourses(), profile.zone(), reset);
  }

  /**
   * The tasks the next plan starts from, before any reported progress (ADR 0011). Reviews are not
   * among them: the planner adds them.
   *
   * @throws edu.uoc.planner.availability.AvailabilityConfigException without a usable profile
   */
  public List<PlanTask> currentTasks(Instant now) {
    AvailabilityProfile profile = availability.profile();
    List<StoredChallenge> challenges = catalog.selectedChallenges();
    Map<Long, ChallengeSpec> valid = new HashMap<>();
    for (StoredChallenge challenge : challenges) {
      specs
          .latest(challenge.id())
          .flatMap(StoredSpec::spec)
          .ifPresent(s -> valid.put(challenge.id(), s));
    }
    return PlanTaskAdapter.adapt(challenges, valid, profile, catalog.selectedCourseCodes(), now)
        .tasks();
  }

  /** Version of the challenge's spec the plan uses; empty if it has no valid one. */
  public Optional<Integer> specVersion(long challengeId) {
    return specs.latest(challengeId).filter(StoredSpec::valid).map(StoredSpec::version);
  }

  /**
   * Reported time left by task. Progress on the tasks of a challenge whose spec has been extracted
   * again is dropped: its task ids may now name other tasks.
   */
  private Map<String, Duration> progress(
      List<StoredChallenge> challenges,
      Map<Long, Optional<StoredSpec>> latest,
      List<String> reset) {
    Map<Long, StoredChallenge> byId = new HashMap<>();
    challenges.forEach(c -> byId.put(c.id(), c));
    Map<String, Duration> left = new HashMap<>();
    for (ProgressStore.Entry entry : progress.all()) {
      StoredChallenge challenge =
          entry.challengeId() == null ? null : byId.get(entry.challengeId());
      Optional<StoredSpec> spec = challenge == null ? Optional.empty() : latest.get(challenge.id());
      if (entry.specVersion() != null
          && spec.isPresent()
          && spec.get().valid()
          && spec.get().version() != entry.specVersion()) {
        progress.delete(entry.taskId());
        String name = challenge.courseShortCode() + " · " + challenge.name();
        if (!reset.contains(name)) {
          reset.add(name);
        }
        continue;
      }
      left.put(entry.taskId(), Duration.ofMinutes(entry.remainingMinutes()));
    }
    return left;
  }

  private static ChallengeStatus statusWithoutRisk(Optional<StoredSpec> spec) {
    if (spec.isEmpty()) {
      return ChallengeStatus.SYNCED;
    }
    return spec.get().valid() ? ChallengeStatus.EXTRACTED : ChallengeStatus.NEEDS_REVIEW;
  }
}
