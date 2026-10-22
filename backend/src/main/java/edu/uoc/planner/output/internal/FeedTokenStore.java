package edu.uoc.planner.output.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import edu.uoc.planner.shared.SecretCipher;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The random token in the feed URLs (32 bytes, design section 9), kept encrypted in {@code
 * user_settings}. Anyone with the URL can read the plan, so it is compared in constant time and
 * never logged.
 */
@Component
class FeedTokenStore {

  static final String KEY = "output.ics-feed-token";
  private static final String CONTEXT = "user_settings.value:" + KEY;

  private final JdbcClient jdbc;
  private final SecretCipher cipher;
  private final SecureRandom random = new SecureRandom();

  FeedTokenStore(JdbcClient jdbc, SecretCipher cipher) {
    this.jdbc = jdbc;
    this.cipher = cipher;
  }

  /** The token, created on first use. */
  String token() {
    return stored()
        .orElseGet(
            () -> {
              byte[] bytes = new byte[32];
              random.nextBytes(bytes);
              String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
              jdbc.sql(
                      "INSERT INTO user_settings (key, value) VALUES (?, ?) ON CONFLICT (key) DO NOTHING")
                  .params(KEY, cipher.encrypt(token.getBytes(UTF_8), CONTEXT))
                  .update();
              return stored().orElseThrow();
            });
  }

  boolean matches(String candidate) {
    if (candidate == null || candidate.isEmpty()) {
      return false;
    }
    return MessageDigest.isEqual(token().getBytes(UTF_8), candidate.getBytes(UTF_8));
  }

  private Optional<String> stored() {
    return jdbc.sql("SELECT value FROM user_settings WHERE key = ?")
        .param(KEY)
        .query(byte[].class)
        .optional()
        .map(blob -> new String(cipher.decrypt(blob, CONTEXT), UTF_8));
  }
}
