package edu.uoc.planner.scheduling.internal;

import edu.uoc.planner.availability.AvailabilityConfigException;
import edu.uoc.planner.canvas.CanvasCatalog;
import edu.uoc.planner.canvas.CanvasSessionExpiredException;
import edu.uoc.planner.canvas.StoredChallenge;
import edu.uoc.planner.extraction.ChallengeSpec;
import edu.uoc.planner.extraction.SpecStore;
import edu.uoc.planner.extraction.StoredSpec;
import edu.uoc.planner.output.MailNotConfiguredException;
import edu.uoc.planner.output.WeeklyReport;
import edu.uoc.planner.output.WeeklyReportMailer;
import edu.uoc.planner.planning.PlanTrigger;
import edu.uoc.planner.scheduling.PipelineReport;
import edu.uoc.planner.scheduling.StudyPipeline;
import java.io.PrintStream;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;

/** {@code make report}: sync, extract and plan as {@code make plan}, then email the weekly PDF. */
@Component
@ConditionalOnProperty(name = "planner.command", havingValue = "report")
final class ReportCommand implements ApplicationRunner, ExitCodeGenerator {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d/M");

  private final StudyPipeline pipeline;
  private final CanvasCatalog catalog;
  private final SpecStore specs;
  private final WeeklyReportMailer mailer;
  private final Clock clock;
  private final PrintStream out;
  private int exitCode;

  @Autowired
  ReportCommand(
      StudyPipeline pipeline,
      CanvasCatalog catalog,
      SpecStore specs,
      WeeklyReportMailer mailer,
      Clock clock) {
    this(pipeline, catalog, specs, mailer, clock, System.out);
  }

  ReportCommand(
      StudyPipeline pipeline,
      CanvasCatalog catalog,
      SpecStore specs,
      WeeklyReportMailer mailer,
      Clock clock,
      PrintStream out) {
    this.pipeline = pipeline;
    this.catalog = catalog;
    this.specs = specs;
    this.mailer = mailer;
    this.clock = clock;
    this.out = out;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      mailer.requireConfigured();
      PipelineReport pipelineReport = pipeline.run(PlanTrigger.MANUAL);
      if (pipelineReport.sync() == null) {
        out.println(
            "Canvas no disponible ("
                + pipelineReport.syncProblem()
                + "): el plan usa los datos guardados.");
      }
      WeeklyReport report = report(pipelineReport);
      mailer.send(report);
      out.println(
          "Plan de la semana del "
              + DATE.format(report.weekStart())
              + " enviado por correo: "
              + plural(report.sessions().size(), "sesión", "sesiones")
              + ", "
              + plural(report.deadlines().size(), "entrega", "entregas")
              + " y "
              + plural(report.atRisk().size(), "reto en riesgo", "retos en riesgo")
              + ".");
      exitCode = 0;
    } catch (MailNotConfiguredException e) {
      fail(e.getMessage());
    } catch (CanvasSessionExpiredException e) {
      fail("No hay una sesión válida de la UOC. Ejecuta make login y vuelve a probar.");
    } catch (AvailabilityConfigException e) {
      fail("Revisa tu disponibilidad: " + e.getMessage());
    } catch (MailAuthenticationException e) {
      fail(
          "El servidor de correo rechaza el usuario o la contraseña: revisa PLANNER_SMTP_USER y"
              + " PLANNER_SMTP_PASSWORD en .env.");
    } catch (MailException e) {
      fail(
          "No se ha podido enviar el correo: revisa PLANNER_SMTP_HOST, PLANNER_SMTP_PORT y"
              + " PLANNER_MAIL_TO en .env, y la conexión.");
    }
  }

  private WeeklyReport report(PipelineReport pipelineReport) {
    List<StoredChallenge> challenges = catalog.selectedChallenges();
    Map<Long, ChallengeSpec> valid = new HashMap<>();
    for (StoredChallenge challenge : challenges) {
      specs
          .latest(challenge.id())
          .flatMap(StoredSpec::spec)
          .ifPresent(s -> valid.put(challenge.id(), s));
    }
    return WeeklyReportBuilder.build(
        pipelineReport.plan().plan(),
        pipelineReport.plan().zone(),
        challenges,
        valid,
        clock.instant());
  }

  private static String plural(int count, String one, String many) {
    return count + " " + (count == 1 ? one : many);
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
