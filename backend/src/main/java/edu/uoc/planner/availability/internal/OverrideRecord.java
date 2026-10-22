package edu.uoc.planner.availability.internal;

import edu.uoc.planner.availability.AvailabilityOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;

/** Row of {@code availability_override}. Not final: Hibernate may proxy it. */
@Entity
@Table(name = "availability_override")
public class OverrideRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "date", nullable = false)
  private LocalDate date;

  @Column(name = "start_time")
  private LocalTime startTime;

  @Column(name = "end_time")
  private LocalTime endTime;

  @Enumerated(EnumType.STRING)
  @Column(name = "effect", nullable = false)
  private AvailabilityOverride.Effect effect;

  protected OverrideRecord() {}

  public AvailabilityOverride toOverride() {
    return new AvailabilityOverride(date, startTime, endTime, effect);
  }
}
