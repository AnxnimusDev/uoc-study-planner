package edu.uoc.planner.output.internal;

import static org.assertj.core.api.Assertions.assertThat;

import edu.uoc.planner.TestcontainersConfiguration;
import edu.uoc.planner.shared.SecretCipher;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, FeedTokenStore.class, FeedTokenStoreTest.Cipher.class})
class FeedTokenStoreTest {

  @Autowired private FeedTokenStore tokens;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void createsA256BitTokenOnce() {
    String token = tokens.token();

    assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
    assertThat(tokens.token()).isEqualTo(token);
  }

  @Test
  void storesItEncrypted() {
    String token = tokens.token();

    byte[] stored =
        jdbc.queryForObject(
            "SELECT value FROM user_settings WHERE key = 'output.ics-feed-token'", byte[].class);
    assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain(token);
  }

  @Test
  void matchesOnlyTheRightToken() {
    String token = tokens.token();

    assertThat(tokens.matches(token)).isTrue();
    assertThat(tokens.matches(token + "x")).isFalse();
    assertThat(tokens.matches("")).isFalse();
    assertThat(tokens.matches(null)).isFalse();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Cipher {

    @Bean
    SecretCipher secretCipher() {
      return SecretCipher.fromBase64Key(Base64.getEncoder().encodeToString(new byte[32]));
    }
  }
}
