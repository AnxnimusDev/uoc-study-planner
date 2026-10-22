package edu.uoc.planner.auth.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import edu.uoc.planner.shared.SecretCipher;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The encrypted Canvas cookie in {@code session_secret}. Only one session is kept. */
@Service
public class SessionStore {

  /** Associated data for {@link SecretCipher}: ties the blob to this column. */
  private static final String CONTEXT = "session_secret.cookie_blob";

  private final SessionSecretRepository repository;
  private final SecretCipher cipher;

  SessionStore(SessionSecretRepository repository, SecretCipher cipher) {
    this.repository = repository;
    this.cipher = cipher;
  }

  /** Stores a freshly validated cookie header, dropping any previous session. */
  @Transactional
  public void replace(String cookieHeader, Instant capturedAt) {
    repository.deleteAllInBatch();
    repository.save(
        new SessionSecret(cipher.encrypt(cookieHeader.getBytes(UTF_8), CONTEXT), capturedAt));
  }

  /** Decrypted cookie header of the current session, empty when none or expired. */
  @Transactional(readOnly = true)
  public Optional<String> currentCookie() {
    return repository
        .findFirstByExpiredAtIsNullOrderByCapturedAtDesc()
        .map(s -> new String(cipher.decrypt(s.cookieBlob(), CONTEXT), UTF_8));
  }

  @Transactional
  public void markExpired(Instant at) {
    repository.findByExpiredAtIsNull().forEach(s -> s.expire(at));
  }
}
