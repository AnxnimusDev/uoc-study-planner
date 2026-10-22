package edu.uoc.planner.canvas.internal;

import edu.uoc.planner.canvas.CanvasException;
import edu.uoc.planner.canvas.CanvasSessionExpiredException;
import edu.uoc.planner.canvas.CanvasSync;
import edu.uoc.planner.canvas.SyncReport;
import edu.uoc.planner.canvas.SyncReport.CourseSync;
import java.io.PrintStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@code make sync}: reads Canvas into the database and prints a summary per course. */
@Component
@ConditionalOnProperty(name = "planner.command", havingValue = "sync")
final class SyncCommand implements ApplicationRunner, ExitCodeGenerator {

  private final CanvasSync sync;
  private final PrintStream out;
  private int exitCode;

  @Autowired
  SyncCommand(CanvasSync sync) {
    this(sync, System.out);
  }

  SyncCommand(CanvasSync sync, PrintStream out) {
    this.sync = sync;
    this.out = out;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      SyncReport report = sync.sync();
      out.println("Sesión válida. " + report.courses().size() + " asignaturas sincronizadas:");
      report.courses().forEach(this::print);
      exitCode = 0;
    } catch (CanvasSessionExpiredException e) {
      out.println("No hay una sesión válida de la UOC. Ejecuta make login y vuelve a probar.");
      exitCode = 1;
    } catch (CanvasException e) {
      out.println("No se ha podido leer el aula: " + e.getMessage());
      exitCode = 1;
    }
  }

  private void print(CourseSync course) {
    out.println(
        "  · "
            + course.name()
            + ": "
            + course.challenges()
            + " retos, "
            + course.deliverables()
            + " entregas, "
            + course.pagesChanged()
            + " páginas nuevas o cambiadas");
    if (!course.missingPages().isEmpty()) {
      out.println("      páginas no disponibles: " + String.join(", ", course.missingPages()));
    }
    if (!course.unresolvedAssignments().isEmpty()) {
      out.println("      entregas no encontradas: " + course.unresolvedAssignments());
    }
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }
}
