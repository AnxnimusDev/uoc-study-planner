package edu.uoc.planner.availability;

import static org.assertj.core.api.Assertions.assertThat;

import edu.uoc.planner.TestcontainersConfiguration;
import edu.uoc.planner.availability.internal.AvailabilityConfiguration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** Profile from the YAML file plus overrides from {@code availability_override}. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  TestcontainersConfiguration.class,
  AvailabilityConfiguration.class,
  AvailabilityService.class
})
@TestPropertySource(properties = "planner.availability.file=../config/availability.example.yml")
class AvailabilityServiceTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);

  @Autowired private AvailabilityService availability;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void appliesTheStoredOverrides() {
    jdbc.update(
        "INSERT INTO availability_override (date, effect) VALUES (?, 'BUSY')", MONDAY.plusDays(1));
    jdbc.update(
        "INSERT INTO availability_override (date, start_time, end_time, effect) VALUES (?, '09:00', '10:30', 'FREE')",
        MONDAY);

    List<FreeSlot> slots = availability.freeSlots(MONDAY, MONDAY.plusDays(2));

    ZoneId madrid = ZoneId.of("Europe/Madrid");
    assertThat(slots)
        .allSatisfy(s -> assertThat(s.start().atZone(madrid).toLocalDate()).isEqualTo(MONDAY));
    assertThat(slots.getFirst().end().atZone(madrid).toLocalTime()).hasToString("10:30");
  }

  @Test
  void exposesTheProfile() {
    assertThat(availability.profile().maxDailyHours()).isEqualTo(3);
  }
}
