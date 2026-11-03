package edu.uoc.planner.scheduling.internal;

import edu.uoc.planner.availability.AvailabilityConfigException;
import edu.uoc.planner.extraction.ChallengeSpec.TaskKind;
import edu.uoc.planner.planning.PlanTask;
import edu.uoc.planner.scheduling.PlanRun;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@code make done TASK=7:t1} and {@code make left TASK=7:t3 HOURS=1.5} (ADR 0011): stores what the
 * student reports. The next {@code make plan} takes it into account.
 */
@Component
@ConditionalOnProperty(name = "planner.command", havingValue = "progress")
final class ProgressCommand implements ApplicationRunner, ExitCodeGenerator {

  private static final Set<TaskKind> FINAL_KINDS = Set.of(TaskKind.QUIZ, TaskKind.SUBMISSION);

  /** What progress can be recorded on: a task of the plan, or the review the planner adds. */
  private record Target(String id, Long challengeId, Integer specVersion, String title) {}

  private final PlanRun plans;
  private final ProgressStore store;
  private final Clock clock;
  private final PrintStream out;
  private int exitCode;

  @Autowired
  ProgressCommand(PlanRun plans, ProgressStore store, Clock clock) {
    this(plans, store, clock, System.out);
  }

  ProgressCommand(PlanRun plans, ProgressStore store, Clock clock, PrintStream out) {
    this.plans = plans;
    this.store = store;
    this.clock = clock;
    this.out = out;
  }

  @Override
  public void run(ApplicationArguments args) {
    String id = option(args, "planner.task");
    String hours = option(args, "planner.hours");
    if (id.isEmpty()) {
      fail("Indica la tarea: make done TASK=7:t1 o make left TASK=7:t1 HOURS=1.5");
      return;
    }
    if (id.startsWith("weekly:")) {
      fail("El tiempo semanal se reparte cada semana: no admite avance.");
      return;
    }
    Long minutes = minutes(hours);
    if (minutes == null) {
      fail("HOURS debe ser un número de horas, por ejemplo HOURS=1.5");
      return;
    }
    Instant now = clock.instant();
    Optional<Target> target;
    try {
      target = target(id, plans.currentTasks(now));
    } catch (AvailabilityConfigException e) {
      fail("Revisa tu disponibilidad: " + e.getMessage());
      return;
    }
    if (target.isEmpty()) {
      fail("No hay ninguna tarea " + id + " en el plan actual. Copia el id de make plan.");
      return;
    }
    Target t = target.get();
    store.save(
        new ProgressStore.Entry(t.id(), t.challengeId(), t.specVersion(), minutes.intValue(), now));
    out.println(
        t.id() + " · " + t.title() + ": " + (minutes == 0 ? "hecha" : "quedan " + format(minutes)));
    out.println("El siguiente make plan lo tendrá en cuenta.");
    exitCode = 0;
  }

  private Optional<Target> target(String id, List<PlanTask> tasks) {
    if (id.startsWith("review:")) {
      return review(id, tasks);
    }
    return tasks.stream()
        .filter(t -> t.id().equals(id) && !t.id().startsWith("weekly:"))
        .findFirst()
        .map(
            t -> {
              Long challenge = t.challengeId() == 0 ? null : t.challengeId();
              // Ids such as 7:t3 come from the extracted spec; quiz:… and review:… do not.
              Integer version =
                  id.matches("\\d+:.*") ? plans.specVersion(t.challengeId()).orElse(null) : null;
              return new Target(t.id(), challenge, version, t.title());
            });
  }

  /** "review:7:assignment:1007498": the planner adds it before a graded deliverable. */
  private static Optional<Target> review(String id, List<PlanTask> tasks) {
    String[] parts = id.split(":", 3);
    if (parts.length < 3 || !parts[1].matches("\\d+")) {
      return Optional.empty();
    }
    long challenge = Long.parseLong(parts[1]);
    List<PlanTask> group =
        tasks.stream()
            .filter(
                t ->
                    t.challengeId() == challenge
                        && t.graded()
                        && parts[2].equals(t.deliverableRef())
                        && t.kind() != TaskKind.VIDEO_RECORDING)
            .toList();
    if (group.isEmpty()) {
      return Optional.empty();
    }
    PlanTask anchor =
        group.stream()
            .filter(t -> FINAL_KINDS.contains(t.kind()))
            .findFirst()
            .orElse(group.getFirst());
    return Optional.of(new Target(id, challenge, null, "Repaso antes de: " + anchor.title()));
  }

  /** Empty or zero: done. Accepts "1.5" and "1,5". Null if not a number of hours. */
  private static Long minutes(String hours) {
    if (hours.isEmpty()) {
      return 0L;
    }
    try {
      double value = Double.parseDouble(hours.replace(',', '.'));
      return value < 0 || Double.isNaN(value) || Double.isInfinite(value)
          ? null
          : Math.round(value * 60);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String format(long minutes) {
    if (minutes < 60) {
      return minutes + " min";
    }
    long rest = minutes % 60;
    return minutes / 60 + " h" + (rest == 0 ? "" : String.format(" %02d", rest));
  }

  private static String option(ApplicationArguments args, String name) {
    List<String> values = args.getOptionValues(name);
    return values == null || values.isEmpty() ? "" : values.getFirst().strip();
  }

  private void fail(String message) {
    out.println(message);
    exitCode = 1;
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }
}
