package edu.uoc.planner.scheduling.internal;

import edu.uoc.planner.availability.AvailabilityConfigException;
import edu.uoc.planner.canvas.CanvasSessionExpiredException;
import edu.uoc.planner.planning.AtRiskTask;
import edu.uoc.planner.planning.Notice;
import edu.uoc.planner.planning.PlanTrigger;
import edu.uoc.planner.planning.PlannedSession;
import edu.uoc.planner.scheduling.ExtractionReport.Outcome;
import edu.uoc.planner.scheduling.PipelineReport;
import edu.uoc.planner.scheduling.StudyPipeline;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@code make plan}: sync, extract and plan, then show the next seven days. */
@Component
@ConditionalOnProperty(name = "planner.command", havingValue = "plan")
final class PlanCommand implements ApplicationRunner, ExitCodeGenerator {

  private static final Locale SPANISH = Locale.forLanguageTag("es-ES");
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d/M", SPANISH);
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

  private final StudyPipeline pipeline;
  private final Clock clock;
  private final PrintStream out;
  private int exitCode;

  @Autowired
  PlanCommand(StudyPipeline pipeline, Clock clock) {
    this(pipeline, clock, System.out);
  }

  PlanCommand(StudyPipeline pipeline, Clock clock, PrintStream out) {
    this.pipeline = pipeline;
    this.clock = clock;
    this.out = out;
  }

  @Override
  public void run(ApplicationArguments args) {
    PipelineReport report;
    try {
      report = pipeline.run(PlanTrigger.MANUAL);
    } catch (CanvasSessionExpiredException e) {
      out.println("No hay una sesión válida de la UOC. Ejecuta make login y vuelve a probar.");
      exitCode = 1;
      return;
    } catch (AvailabilityConfigException e) {
      out.println("Revisa tu disponibilidad: " + e.getMessage());
      exitCode = 1;
      return;
    }
    print(report);
    exitCode = 0;
  }

  private void print(PipelineReport report) {
    if (report.sync() != null) {
      out.println("Canvas: " + report.sync().courses().size() + " asignaturas sincronizadas.");
    } else {
      out.println(
          "Canvas no disponible ("
              + report.syncProblem()
              + "): se planifica con los datos guardados.");
    }
    Map<Outcome, Long> extracted =
        report.extraction().items().stream()
            .collect(Collectors.groupingBy(i -> i.outcome(), Collectors.counting()));
    out.println(
        "Enunciados: "
            + extracted.getOrDefault(Outcome.EXTRACTED, 0L)
            + " extraídos, "
            + extracted.getOrDefault(Outcome.NEEDS_REVIEW, 0L)
            + " para revisar, "
            + extracted.getOrDefault(Outcome.NO_STATEMENT, 0L)
            + " sin enunciado.");
    if (report.extraction().stoppedBecause() != null) {
      out.println("  Extracción detenida: " + report.extraction().stoppedBecause());
    }

    ZoneId zone = report.plan().zone();
    Instant now = clock.instant();
    LocalDate today = now.atZone(zone).toLocalDate();
    Map<LocalDate, List<PlannedSession>> week =
        report.plan().plan().sessions().stream()
            .filter(s -> s.start().atZone(zone).toLocalDate().isBefore(today.plusDays(7)))
            .collect(
                Collectors.groupingBy(
                    s -> s.start().atZone(zone).toLocalDate(), TreeMap::new, Collectors.toList()));
    out.println();
    out.println("Plan de los próximos 7 días:");
    if (week.isEmpty()) {
      out.println("  (sin sesiones)");
    }
    week.forEach(
        (day, sessions) -> {
          String label = DAY.format(day);
          out.println("  " + Character.toUpperCase(label.charAt(0)) + label.substring(1));
          sessions.forEach(
              s ->
                  out.println(
                      "    "
                          + TIME.format(s.start().atZone(zone))
                          + "–"
                          + TIME.format(s.end().atZone(zone))
                          + " · "
                          + s.courseCode()
                          + " · "
                          + s.title()));
        });
    List<AtRiskTask> risks = report.plan().plan().atRisk();
    if (!risks.isEmpty()) {
      out.println();
      out.println("En riesgo (no cabe antes del plazo):");
      risks.forEach(
          r ->
              out.println(
                  "  · "
                      + r.taskId()
                      + ": faltan "
                      + r.missing().toMinutes()
                      + " min ("
                      + r.reason()
                      + ")"));
    }
    if (!report.plan().unestimated().isEmpty()) {
      out.println();
      out.println("Sin estimar (no planificado, revisa el enunciado):");
      report.plan().unestimated().forEach(u -> out.println("  · " + u));
    }
    List<Notice> notices = report.plan().plan().notices();
    if (!notices.isEmpty()) {
      out.println();
      out.println("Avisos:");
      notices.forEach(n -> out.println("  · " + n.message()));
    }
    out.println();
    out.println("Calendarios: make feed muestra las URL para suscribirte.");
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }
}
