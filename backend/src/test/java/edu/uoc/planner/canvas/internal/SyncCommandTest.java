package edu.uoc.planner.canvas.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.uoc.planner.canvas.CanvasSessionExpiredException;
import edu.uoc.planner.canvas.CanvasSync;
import edu.uoc.planner.canvas.CanvasUnavailableException;
import edu.uoc.planner.canvas.SyncReport;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SyncCommandTest {

  private final CanvasSync sync = mock(CanvasSync.class);
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();

  @Test
  void summarizesEachSyncedCourse() {
    when(sync.sync())
        .thenReturn(
            new SyncReport(
                List.of(
                    new SyncReport.CourseSync(
                        94063,
                        "05.557 - Àlgebra - Aula 2",
                        5,
                        17,
                        3,
                        1,
                        List.of("ges-guies-destudi-setmanal-r1"),
                        List.of()))));

    SyncCommand command = command();
    command.run(null);

    assertThat(command.getExitCode()).isZero();
    assertThat(output())
        .contains("1 asignaturas sincronizadas")
        .contains("05.557 - Àlgebra - Aula 2: 5 retos, 17 entregas, 1 páginas nuevas o cambiadas")
        .contains("páginas no disponibles: ges-guies-destudi-setmanal-r1");
  }

  @Test
  void asksToLogInWhenTheSessionExpired() {
    when(sync.sync()).thenThrow(new CanvasSessionExpiredException("401"));

    SyncCommand command = command();
    command.run(null);

    assertThat(command.getExitCode()).isEqualTo(1);
    assertThat(output()).contains("make login");
  }

  @Test
  void explainsWhenCanvasIsDown() {
    when(sync.sync()).thenThrow(new CanvasUnavailableException("Canvas error: 503"));

    SyncCommand command = command();
    command.run(null);

    assertThat(command.getExitCode()).isEqualTo(1);
    assertThat(output()).contains("No se ha podido leer el aula", "503");
  }

  private SyncCommand command() {
    return new SyncCommand(sync, new PrintStream(output, true, StandardCharsets.UTF_8));
  }

  private String output() {
    return output.toString(StandardCharsets.UTF_8);
  }
}
