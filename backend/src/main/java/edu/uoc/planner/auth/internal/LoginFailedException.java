package edu.uoc.planner.auth.internal;

/** The interactive login did not produce a valid session. */
final class LoginFailedException extends RuntimeException {

  LoginFailedException(String message) {
    super(message);
  }

  LoginFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
