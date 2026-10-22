package edu.uoc.planner.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import edu.uoc.planner.shared.SecretCipher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;

class SecretKeyFailureAnalyzerTest {

  @Test
  void explainsHowToCreateTheKey() {
    Throwable failure =
        new IllegalStateException(
            "bean creation", catchThrowable(() -> SecretCipher.fromBase64Key("")));

    FailureAnalysis analysis = new SecretKeyFailureAnalyzer().analyze(failure);

    assertThat(analysis.getDescription()).contains("PLANNER_SECRET_KEY");
    assertThat(analysis.getAction()).contains("openssl rand -base64 32");
  }
}
