package edu.uoc.planner.scheduling.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.uoc.planner.canvas.CanvasSessionExpiredException;
import edu.uoc.planner.canvas.SyncReport;
import edu.uoc.planner.extraction.ChallengeSpec.TaskKind;
import edu.uoc.planner.planning.AtRiskTask;
import edu.uoc.planner.planning.Plan;
import edu.uoc.planner.planning.PlannedSession;
import edu.uoc.planner.scheduling.ExtractionReport;
import edu.uoc.planner.scheduling.PipelineReport;
import edu.uoc.planner.scheduling.PlanReport;
import edu.uoc.planner.scheduling.StudyPipeline;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanCommandTest {

  private static final Instant NOW = Instant.parse("2026-10-26T07:00:00Z");
  private final StudyPipeline pipeline = mock(StudyPipeline.class);
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();

  @Test
  void showsTheNextSevenDaysRisksAndUnestimatedWork() {
    PlannedSession monday =
        new PlannedSession(
            "1:t1",
            1,
            "05.557",
            "Llegir la GES",
            TaskKind.READING,
            Instant.parse("2026-10-26T17:15:00Z"),
            Instant.parse("2026-10-26T18:00:00Z"),
            false);
    Plan plan =
        new Plan(
            List.of(monday),
            List.of(
                new AtRiskTask("2:t1", 2, Duration.ofMinutes(90), "No cabe antes del plazo"),
                new AtRiskTask(
                    "weekly:20.043:2026-W44",
                    0,
                    Duration.ofMinutes(60),
                    "No cabe antes del plazo")),
            List.of(),
            List.of(),
            List.of(2L),
            Duration.ofMinutes(90));
    when(pipeline.run(any()))
        .thenReturn(
            new PipelineReport(
                new SyncReport(List.of()),
                null,
                new ExtractionReport(
                    List.of(
                        new ExtractionReport.Item(
                            3, "05.557", "Repte 1", ExtractionReport.Outcome.PENDING),
                        new ExtractionReport.Item(
                            4, "20.439", "Challenge 1", ExtractionReport.Outcome.NO_STATEMENT)),
                    "Claude API answered 400: Your credit balance is too low"),
                new PlanReport(
                    plan,
                    List.of("05.590 · Lliurament R1"),
                    List.of("20.999"),
                    ZoneId.of("Europe/Madrid"))));

    PlanCommand command = command();
    command.run(null);

    assertThat(command.getExitCode()).isZero();
    assertThat(output())
        .contains("0 extraídos, 0 para revisar, 1 sin enunciado, 1 pendientes")
        .contains("Extracción detenida: Claude API answered 400: Your credit balance is too low")
        .contains("Lunes 26/10", "18:15–19:00 · 05.557 · Llegir la GES")
        .contains("En riesgo", "2:t1: faltan 90 min")
        .doesNotContain("weekly:20.043:2026-W44: faltan")
        .contains("Sin estimar", "05.590 · Lliurament R1")
        .contains("Tiempo semanal sin hueco", "20.043, semana 2026-W44: faltan 60 min")
        .contains("weeklyStudy: 20.999 no es una asignatura seleccionada")
        .contains("make feed");
  }

  @Test
  void asksToLogInWhenTheSessionExpired() {
    when(pipeline.run(any())).thenThrow(new CanvasSessionExpiredException("401"));

    PlanCommand command = command();
    command.run(null);

    assertThat(command.getExitCode()).isEqualTo(1);
    assertThat(output()).contains("make login");
  }

  private PlanCommand command() {
    return new PlanCommand(
        pipeline,
        Clock.fixed(NOW, ZoneOffset.UTC),
        new PrintStream(output, true, StandardCharsets.UTF_8));
  }

  private String output() {
    return output.toString(StandardCharsets.UTF_8);
  }
}
