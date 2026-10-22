package edu.uoc.planner.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;

import edu.uoc.planner.TestcontainersConfiguration;
import edu.uoc.planner.shared.SecretCipher;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, SessionStore.class, SessionStoreTest.Cipher.class})
class SessionStoreTest {

  private static final Instant LOGIN = Instant.parse("2026-10-22T08:00:00Z");

  @Autowired private SessionStore store;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void returnsNothingBeforeTheFirstLogin() {
    assertThat(store.currentCookie()).isEmpty();
  }

  @Test
  void returnsTheStoredCookieDecrypted() {
    store.replace("canvas_session=abc; _csrf_token=xyz", LOGIN);

    assertThat(store.currentCookie()).hasValue("canvas_session=abc; _csrf_token=xyz");
  }

  @Test
  void keepsTheCookieEncryptedInTheDatabase() {
    store.replace("canvas_session=abc", LOGIN);

    byte[] blob = jdbc.queryForObject("SELECT cookie_blob FROM session_secret", byte[].class);
    assertThat(new String(blob, java.nio.charset.StandardCharsets.ISO_8859_1))
        .doesNotContain("canvas_session");
    assertThat(
            jdbc.queryForObject(
                "SELECT last_valid_at = captured_at FROM session_secret", Boolean.class))
        .isTrue();
  }

  @Test
  void replacesThePreviousSessionOnLogin() {
    store.replace("canvas_session=old", LOGIN);
    store.replace("canvas_session=new", LOGIN.plusSeconds(3600));

    assertThat(store.currentCookie()).hasValue("canvas_session=new");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM session_secret", Integer.class)).isOne();
  }

  @Test
  void forgetsTheCookieOnceExpired() {
    store.replace("canvas_session=abc", LOGIN);
    Instant expiry = LOGIN.plusSeconds(7200);

    store.markExpired(expiry);

    assertThat(store.currentCookie()).isEmpty();
    assertThat(
            jdbc.queryForObject("SELECT expired_at FROM session_secret", java.sql.Timestamp.class)
                .toInstant())
        .isEqualTo(expiry);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Cipher {

    @Bean
    SecretCipher secretCipher() {
      return SecretCipher.fromBase64Key(Base64.getEncoder().encodeToString(new byte[32]));
    }
  }
}
