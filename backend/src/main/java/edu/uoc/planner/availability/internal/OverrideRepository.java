package edu.uoc.planner.availability.internal;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverrideRepository extends JpaRepository<OverrideRecord, Long> {

  /** Overrides from {@code from} (inclusive) to {@code to} (exclusive). */
  List<OverrideRecord> findByDateGreaterThanEqualAndDateLessThanOrderByDate(
      LocalDate from, LocalDate to);
}
