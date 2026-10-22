package edu.uoc.planner.canvas.internal;

import edu.uoc.planner.canvas.CanvasException;
import edu.uoc.planner.canvas.CanvasSessionExpiredException;
import edu.uoc.planner.canvas.Course;
import java.io.PrintStream;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@code make sync}. For now it checks the session and lists the courses of the current term. */
@Component
@ConditionalOnProperty(name = "planner.command", havingValue = "sync")
final class SyncCommand implements ApplicationRunner, ExitCodeGenerator {

  private final CanvasClient client;
  private final CanvasNormalizer normalizer = new CanvasNormalizer();
  private final PrintStream out;
  private int exitCode;

  @Autowired
  SyncCommand(CanvasClient client) {
    this(client, System.out);
  }

  SyncCommand(CanvasClient client, PrintStream out) {
    this.client = client;
    this.out = out;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      client.self();
      List<Course> courses = normalizer.courses(client.courses());
      out.println("Sesión válida. " + courses.size() + " asignaturas en el semestre actual:");
      courses.forEach(c -> out.println("  · " + c.name()));
      exitCode = 0;
    } catch (CanvasSessionExpiredException e) {
      out.println("No hay una sesión válida de la UOC. Ejecuta make login y vuelve a probar.");
      exitCode = 1;
    } catch (CanvasException e) {
      out.println("No se ha podido leer el aula: " + e.getMessage());
      exitCode = 1;
    }
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }
}
