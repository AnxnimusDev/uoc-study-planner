package edu.uoc.planner.auth.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Row of {@code session_secret}. Not final: Hibernate may proxy it. */
@Entity
@Table(name = "session_secret")
class SessionSecret {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "cookie_blob", nullable = false)
  private byte[] cookieBlob;

  @Column(name = "captured_at", nullable = false)
  private Instant capturedAt;

  @Column(name = "last_valid_at")
  private Instant lastValidAt;

  @Column(name = "expired_at")
  private Instant expiredAt;

  protected SessionSecret() {}

  SessionSecret(byte[] cookieBlob, Instant capturedAt) {
    this.cookieBlob = cookieBlob;
    this.capturedAt = capturedAt;
    this.lastValidAt = capturedAt;
  }

  byte[] cookieBlob() {
    return cookieBlob;
  }

  void expire(Instant at) {
    this.expiredAt = at;
  }
}
