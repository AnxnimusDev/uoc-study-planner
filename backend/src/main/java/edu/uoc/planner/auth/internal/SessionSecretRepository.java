package edu.uoc.planner.auth.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SessionSecretRepository extends JpaRepository<SessionSecret, Long> {

  Optional<SessionSecret> findFirstByExpiredAtIsNullOrderByCapturedAtDesc();

  List<SessionSecret> findByExpiredAtIsNull();
}
