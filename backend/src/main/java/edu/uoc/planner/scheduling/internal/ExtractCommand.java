package edu.uoc.planner.scheduling.internal;

import edu.uoc.planner.scheduling.ExtractionReport;
import edu.uoc.planner.scheduling.ExtractionReport.Outcome;
import edu.uoc.planner.scheduling.ExtractionRun;
import java.io.PrintStream;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@code make extract}: extracts the new or changed statements and prints a summary. */
@Component
@ConditionalOnProperty(name = "planner.command", havingValue = "extract")
final class ExtractCommand implements ApplicationRunner, ExitCodeGenerator {

  private static final Map<Outcome, String> LABELS =
      Map.of(
          Outcome.EXTRACTED, "extraído",
          Outcome.NEEDS_REVIEW, "necesita revisión",
          Outcome.UNCHANGED, "sin cambios",
          Outcome.NO_STATEMENT, "sin enunciado",
          Outcome.EDITED_BY_STUDENT, "corregido por ti, el enunciado ha cambiado: revísalo");

  private final ExtractionRun run;
  private final PrintStream out;
  private int exitCode;

  @Autowired
  ExtractCommand(ExtractionRun run) {
    this(run, System.out);
  }

  ExtractCommand(ExtractionRun run, PrintStream out) {
    this.run = run;
    this.out = out;
  }

  @Override
  public void run(ApplicationArguments args) {
    ExtractionReport report = run.run();
    Map<Outcome, Long> counts =
        report.items().stream()
            .collect(Collectors.groupingBy(ExtractionReport.Item::outcome, Collectors.counting()));
    out.println(
        "Retos: "
            + counts.getOrDefault(Outcome.EXTRACTED, 0L)
            + " extraídos, "
            + counts.getOrDefault(Outcome.NEEDS_REVIEW, 0L)
            + " para revisar, "
            + counts.getOrDefault(Outcome.UNCHANGED, 0L)
            + " sin cambios.");
    report.items().stream()
        .filter(i -> i.outcome() != Outcome.UNCHANGED)
        .forEach(
            i ->
                out.println(
                    "  · "
                        + i.courseCode()
                        + " · "
                        + i.challengeName()
                        + ": "
                        + LABELS.get(i.outcome())));
    if (report.stoppedBecause() != null) {
      out.println("Extracción detenida: " + report.stoppedBecause());
      out.println("Los retos que faltan se extraerán en la próxima ejecución.");
      exitCode = 1;
    } else {
      exitCode = 0;
    }
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }
}
