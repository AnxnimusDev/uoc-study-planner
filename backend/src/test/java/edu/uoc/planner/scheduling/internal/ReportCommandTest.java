package edu.uoc.planner.scheduling.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.uoc.planner.canvas.CanvasCatalog;
import edu.uoc.planner.canvas.CanvasSessionExpiredException;
import edu.uoc.planner.canvas.SyncReport;
import edu.uoc.planner.extraction.ChallengeSpec.TaskKind;
import edu.uoc.planner.extraction.SpecStore;
import edu.uoc.planner.output.MailNotConfiguredException;
import edu.uoc.planner.output.WeeklyReport;
import edu.uoc.planner.output.WeeklyReportMailer;
import edu.uoc.planner.planning.Plan;
import edu.uoc.planner.planning.PlanTrigger;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

class ReportCommandTest {

  private static final Instant NOW = Instant.parse("2026-11-05T18:00:00Z");

  private final StudyPipeline pipeline = mock(StudyPipeline.class);
  private final CanvasCatalog catalog = mock(CanvasCatalog.class);
  private final SpecStore specs = mock(SpecStore.class);
  private final WeeklyReportMailer mailer = mock(WeeklyReportMailer.class);
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();

  @BeforeEach
  void setUp() {
    when(catalog.selectedChallenges()).thenReturn(List.of());
    when(pipeline.run(any())).thenReturn(report(new SyncReport(List.of()), null));
  }

  @Test
  void sendsTheReportOfAFreshPlan() {
    ReportCommand command = run();

    assertThat(command.getExitCode()).isZero();
    verify(pipeline).run(PlanTrigger.MANUAL);
    ArgumentCaptor<WeeklyReport> sent = ArgumentCaptor.forClass(WeeklyReport.class);
    verify(mailer).send(sent.capture());
    assertThat(sent.getValue().weekStart()).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(sent.getValue().sessions()).hasSize(1);
    assertThat(output())
        .contains("Plan de la semana del 5/11 enviado por correo")
        .contains("1 sesión, 0 entregas y 0 retos en riesgo");
  }

  @Test
  void saysSoWhenCanvasCouldNotBeRead() {
    when(pipeline.run(any())).thenReturn(report(null, "Canvas caído"));

    ReportCommand command = run();

    assertThat(command.getExitCode()).isZero();
    verify(mailer).send(any());
    assertThat(output()).contains("Canvas no disponible (Canvas caído)");
  }

  @Test
  void stopsBeforeReplanningWithoutMailSettings() {
    doThrow(new MailNotConfiguredException("define PLANNER_MAIL_TO"))
        .when(mailer)
        .requireConfigured();

    ReportCommand command = run();

    assertThat(command.getExitCode()).isEqualTo(1);
    assertThat(output()).contains("define PLANNER_MAIL_TO");
    verify(pipeline, never()).run(any());
    verify(mailer, never()).send(any());
  }

  @Test
  void asksToLogInWhenTheSessionExpired() {
    when(pipeline.run(any())).thenThrow(new CanvasSessionExpiredException("expired"));

    ReportCommand command = run();

    assertThat(command.getExitCode()).isEqualTo(1);
    assertThat(output()).contains("make login");
    verify(mailer, never()).send(any());
  }

  @Test
  void pointsAtTheCredentialsWhenTheServerRejectsThem() {
    doThrow(new MailAuthenticationException("535 secret detail")).when(mailer).send(any());

    ReportCommand command = run();

    assertThat(command.getExitCode()).isEqualTo(1);
    assertThat(output())
        .contains("PLANNER_SMTP_USER", "PLANNER_SMTP_PASSWORD")
        .doesNotContain("secret detail");
  }

  @Test
  void pointsAtTheServerWhenTheMailCannotBeSent() {
    doThrow(new MailSendException("connection refused")).when(mailer).send(any());

    ReportCommand command = run();

    assertThat(command.getExitCode()).isEqualTo(1);
    assertThat(output()).contains("PLANNER_SMTP_HOST").doesNotContain("connection refused");
  }

  private ReportCommand run() {
    ReportCommand command =
        new ReportCommand(
            pipeline,
            catalog,
            specs,
            mailer,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new PrintStream(output, true, StandardCharsets.UTF_8));
    command.run(new DefaultApplicationArguments());
    return command;
  }

  private String output() {
    return output.toString(StandardCharsets.UTF_8);
  }

  private static PipelineReport report(SyncReport sync, String problem) {
    PlannedSession session =
        new PlannedSession(
            "1:t1",
            1,
            "05.557",
            "Llegir la GES",
            TaskKind.READING,
            Instant.parse("2026-11-06T17:00:00Z"),
            Instant.parse("2026-11-06T18:00:00Z"),
            false);
    Plan plan =
        new Plan(List.of(session), List.of(), List.of(), List.of(), List.of(), Duration.ZERO);
    return new PipelineReport(
        sync,
        problem,
        new ExtractionReport(List.of(), null),
        new PlanReport(plan, List.of(), List.of(), ZoneId.of("Europe/Madrid")));
  }
}
