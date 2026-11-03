package edu.uoc.planner.scheduling.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.uoc.planner.extraction.ChallengeSpec.TaskKind;
import edu.uoc.planner.planning.PlanTask;
import edu.uoc.planner.scheduling.PlanRun;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class ProgressCommandTest {

  private static final Instant NOW = Instant.parse("2026-11-02T21:00:00Z");

  private final PlanRun plans = mock(PlanRun.class);
  private final ProgressStore store = mock(ProgressStore.class);
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();

  @BeforeEach
  void setUp() {
    when(plans.currentTasks(any()))
        .thenReturn(
            List.of(
                task("7:t1", TaskKind.READING, "Estudiar el Mòdul 3", null),
                task("7:t10", TaskKind.QUIZ, "Resoldre el qüestionari", "assignment:1007498"),
                task("quiz:979687", TaskKind.QUIZ, "QÜESTIONARI 1", "assignment:979687"),
                task("weekly:05.658:2026-W45", TaskKind.READING, "Laboratori", null)));
    when(plans.specVersion(7)).thenReturn(Optional.of(3));
  }

  @Test
  void marksASpecTaskDoneWithItsSpecVersion() {
    ProgressCommand command = run("--planner.task=7:t1");

    assertThat(command.getExitCode()).isZero();
    verify(store).save(new ProgressStore.Entry("7:t1", 7L, 3, 0, NOW));
    assertThat(output()).contains("7:t1 · Estudiar el Mòdul 3: hecha");
  }

  @Test
  void recordsTheTimeLeft() {
    ProgressCommand command = run("--planner.task=7:t1", "--planner.hours=1,5");

    assertThat(command.getExitCode()).isZero();
    verify(store).save(new ProgressStore.Entry("7:t1", 7L, 3, 90, NOW));
    assertThat(output()).contains("7:t1 · Estudiar el Mòdul 3: quedan 1 h 30");
  }

  @Test
  void anExternalQuizDoesNotDependOnTheSpec() {
    run("--planner.task=quiz:979687", "--planner.hours=0.75");

    verify(store).save(new ProgressStore.Entry("quiz:979687", 7L, null, 45, NOW));
  }

  @Test
  void acceptsTheReviewOfAGradedDeliverable() {
    run("--planner.task=review:7:assignment:1007498", "--planner.hours=0");

    verify(store).save(new ProgressStore.Entry("review:7:assignment:1007498", 7L, null, 0, NOW));
    assertThat(output()).contains("Repaso antes de: Resoldre el qüestionari: hecha");
  }

  @Test
  void rejectsATaskThatIsNotInThePlan() {
    ProgressCommand command = run("--planner.task=7:t99");

    assertThat(command.getExitCode()).isEqualTo(1);
    verify(store, never()).save(any());
    assertThat(output()).contains("No hay ninguna tarea 7:t99 en el plan actual");
  }

  @Test
  void rejectsWeeklyStudyTime() {
    ProgressCommand command = run("--planner.task=weekly:05.658:2026-W45");

    assertThat(command.getExitCode()).isEqualTo(1);
    verify(store, never()).save(any());
    assertThat(output()).contains("se reparte cada semana");
  }

  @Test
  void rejectsHoursThatAreNotANumber() {
    ProgressCommand command = run("--planner.task=7:t1", "--planner.hours=mucho");

    assertThat(command.getExitCode()).isEqualTo(1);
    verify(store, never()).save(any());
    assertThat(output()).contains("HOURS");
  }

  @Test
  void asksForTheTask() {
    ProgressCommand command = run("--planner.task=");

    assertThat(command.getExitCode()).isEqualTo(1);
    assertThat(output()).contains("make done TASK=");
  }

  private ProgressCommand run(String... args) {
    ProgressCommand command =
        new ProgressCommand(
            plans,
            store,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new PrintStream(output, true, StandardCharsets.UTF_8));
    command.run(new DefaultApplicationArguments(args));
    return command;
  }

  private String output() {
    return output.toString(StandardCharsets.UTF_8);
  }

  private static PlanTask task(String id, TaskKind kind, String title, String ref) {
    return new PlanTask(
        id,
        id.startsWith("weekly:") ? 0 : 7,
        "05.562",
        20,
        0,
        title,
        kind,
        Duration.ofHours(3),
        null,
        Instant.parse("2026-11-04T22:59:00Z"),
        1,
        null,
        List.of(),
        0,
        ref,
        ref != null);
  }
}
