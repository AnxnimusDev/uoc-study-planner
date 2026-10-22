package edu.uoc.planner.shared;

/** Invalid key, or data that cannot be decrypted. Never carries key material nor plaintext. */
public final class SecretCipherException extends RuntimeException {

  public SecretCipherException(String message) {
    super(message);
  }

  public SecretCipherException(String message, Throwable cause) {
    super(message, cause);
  }
}
