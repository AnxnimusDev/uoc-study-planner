package edu.uoc.planner.output;

/** The weekly email cannot be sent: SMTP or recipients missing. The message says what to set. */
public final class MailNotConfiguredException extends RuntimeException {

  public MailNotConfiguredException(String message) {
    super(message);
  }
}
