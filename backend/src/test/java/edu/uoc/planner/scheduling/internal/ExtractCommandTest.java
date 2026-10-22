package edu.uoc.planner.scheduling.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.uoc.planner.scheduling.ExtractionReport;
import edu.uoc.planner.scheduling.ExtractionReport.Item;
import edu.uoc.planner.scheduling.ExtractionReport.Outcome;
import edu.uoc.planner.scheduling.ExtractionRun;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractCommandTest {

  private final ExtractionRun run = mock(ExtractionRun.class);
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();

  @Test
  void summarizesTheRun() {
    when(run.run())
        .thenReturn(
            new ExtractionReport(
                List.of(
                    new Item(1, "05.557", "Repte 1", Outcome.EXTRACTED),
                    new Item(2, "05.557", "Repte 2", Outcome.UNCHANGED),
                    new Item(3, "05.590", "Repte 1", Outcome.NEEDS_REVIEW)),
                null));

    ExtractCommand command = command();
    command.run(null);

    assertThat(command.getExitCode()).isZero();
    assertThat(output())
        .contains("1 extraídos, 1 para revisar, 1 sin cambios")
        .contains("05.590 · Repte 1: necesita revisión")
        .doesNotContain("Repte 2");
  }

  @Test
  void explainsWhyItStopped() {
    when(run.run()).thenReturn(new ExtractionReport(List.of(), "ANTHROPIC_API_KEY is not set"));

    ExtractCommand command = command();
    command.run(null);

    assertThat(command.getExitCode()).isEqualTo(1);
    assertThat(output())
        .contains("Extracción detenida: ANTHROPIC_API_KEY is not set", "próxima ejecución");
  }

  private ExtractCommand command() {
    return new ExtractCommand(run, new PrintStream(output, true, StandardCharsets.UTF_8));
  }

  private String output() {
    return output.toString(StandardCharsets.UTF_8);
  }
}
