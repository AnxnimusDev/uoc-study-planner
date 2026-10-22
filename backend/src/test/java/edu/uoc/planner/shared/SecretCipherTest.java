package edu.uoc.planner.shared;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

  private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

  private final SecretCipher cipher = SecretCipher.fromBase64Key(KEY);

  @Test
  void decryptsWhatItEncrypts() {
    byte[] encrypted = cipher.encrypt("canvas_session=abc".getBytes(UTF_8), "session_secret");

    assertThat(new String(cipher.decrypt(encrypted, "session_secret"), UTF_8))
        .isEqualTo("canvas_session=abc");
  }

  @Test
  void usesAFreshNonceEveryTime() {
    byte[] plaintext = "same".getBytes(UTF_8);

    assertThat(cipher.encrypt(plaintext, "ctx")).isNotEqualTo(cipher.encrypt(plaintext, "ctx"));
  }

  @Test
  void neverStoresThePlaintext() {
    byte[] encrypted = cipher.encrypt("canvas_session=abc".getBytes(UTF_8), "ctx");

    assertThat(new String(encrypted, UTF_8)).doesNotContain("canvas_session");
  }

  @Test
  void rejectsTamperedData() {
    byte[] encrypted = cipher.encrypt("secret".getBytes(UTF_8), "ctx");
    encrypted[encrypted.length - 1] ^= 1;

    assertThatThrownBy(() -> cipher.decrypt(encrypted, "ctx"))
        .isInstanceOf(SecretCipherException.class);
  }

  @Test
  void rejectsDataEncryptedForAnotherContext() {
    byte[] encrypted = cipher.encrypt("secret".getBytes(UTF_8), "session_secret");

    assertThatThrownBy(() -> cipher.decrypt(encrypted, "user_settings"))
        .isInstanceOf(SecretCipherException.class);
  }

  @Test
  void rejectsDataEncryptedWithAnotherKey() {
    byte[] other = new byte[32];
    other[0] = 1;
    byte[] encrypted =
        SecretCipher.fromBase64Key(Base64.getEncoder().encodeToString(other))
            .encrypt("secret".getBytes(UTF_8), "ctx");

    assertThatThrownBy(() -> cipher.decrypt(encrypted, "ctx"))
        .isInstanceOf(SecretCipherException.class);
  }

  @Test
  void requiresA256BitKey() {
    String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

    assertThatThrownBy(() -> SecretCipher.fromBase64Key(shortKey))
        .isInstanceOf(SecretCipherException.class)
        .hasMessageContaining("32 bytes")
        .hasMessageNotContaining(shortKey);
    assertThatThrownBy(() -> SecretCipher.fromBase64Key(""))
        .isInstanceOf(SecretCipherException.class);
    assertThatThrownBy(() -> SecretCipher.fromBase64Key("not base64!"))
        .isInstanceOf(SecretCipherException.class);
  }
}
