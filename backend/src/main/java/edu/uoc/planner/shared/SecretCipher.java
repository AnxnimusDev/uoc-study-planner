package edu.uoc.planner.shared;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM for secrets at rest (session cookie, user settings). Output: 12-byte random nonce
 * followed by ciphertext and 128-bit tag. The context is bound as associated data, so a value
 * encrypted for one column cannot be decrypted as another.
 */
public final class SecretCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int KEY_BYTES = 32;
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKey key;
  private final SecureRandom random = new SecureRandom();

  private SecretCipher(byte[] key) {
    this.key = new SecretKeySpec(key, "AES");
  }

  /** {@code PLANNER_SECRET_KEY}: 32 random bytes in base64 ({@code openssl rand -base64 32}). */
  public static SecretCipher fromBase64Key(String base64Key) {
    byte[] key;
    try {
      key = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.strip());
    } catch (IllegalArgumentException e) {
      throw new SecretCipherException("PLANNER_SECRET_KEY is not valid base64");
    }
    if (key.length != KEY_BYTES) {
      throw new SecretCipherException(
          "PLANNER_SECRET_KEY must decode to 32 bytes, got " + key.length);
    }
    return new SecretCipher(key);
  }

  public byte[] encrypt(byte[] plaintext, String context) {
    byte[] nonce = new byte[NONCE_BYTES];
    random.nextBytes(nonce);
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      cipher.updateAAD(context.getBytes(UTF_8));
      byte[] ciphertext = cipher.doFinal(plaintext);
      return ByteBuffer.allocate(NONCE_BYTES + ciphertext.length)
          .put(nonce)
          .put(ciphertext)
          .array();
    } catch (GeneralSecurityException e) {
      throw new SecretCipherException("Encryption failed", e);
    }
  }

  public byte[] decrypt(byte[] encrypted, String context) {
    if (encrypted.length <= NONCE_BYTES) {
      throw new SecretCipherException("Encrypted value too short");
    }
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, encrypted, 0, NONCE_BYTES));
      cipher.updateAAD(context.getBytes(UTF_8));
      return cipher.doFinal(encrypted, NONCE_BYTES, encrypted.length - NONCE_BYTES);
    } catch (GeneralSecurityException e) {
      throw new SecretCipherException("Cannot decrypt: wrong key, context or tampered data", e);
    }
  }
}
