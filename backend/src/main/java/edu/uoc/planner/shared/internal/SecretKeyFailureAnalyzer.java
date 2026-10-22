package edu.uoc.planner.shared.internal;

import edu.uoc.planner.shared.SecretCipherException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Turns a missing or invalid {@code PLANNER_SECRET_KEY} into a short startup message. */
class SecretKeyFailureAnalyzer extends AbstractFailureAnalyzer<SecretCipherException> {

  @Override
  protected FailureAnalysis analyze(Throwable rootFailure, SecretCipherException cause) {
    return new FailureAnalysis(
        cause.getMessage(),
        "Define PLANNER_SECRET_KEY en .env con el resultado de: openssl rand -base64 32",
        cause);
  }
}
