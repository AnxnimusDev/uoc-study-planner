package edu.uoc.planner.availability;

import static org.assertj.core.api.Assertions.assertThat;

import edu.uoc.planner.availability.AvailabilityOverride.Effect;
import edu.uoc.planner.availability.internal.AvailabilityProfileLoader;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Week of 2026-10-26 (Monday) with config/availability.example.yml. */
class FreeSlotCalculatorTest {

  private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);
  private static final LocalDate TUESDAY = MONDAY.plusDays(1);
  private static final LocalDate FRIDAY = MONDAY.plusDays(4);
  private static final LocalDate SATURDAY = MONDAY.plusDays(5);
  private static final LocalDate SUNDAY = MONDAY.plusDays(6);

  private static final AvailabilityProfile EXAMPLE =
      AvailabilityProfileLoader.load(Path.of("../config/availability.example.yml"));

  private final FreeSlotCalculator calculator = new FreeSlotCalculator();

  @Nested
  class WeeklyTemplate {

    @Test
    void remoteWorkdayLeavesTheGapsAroundWorkGymAndDinner() {
      assertThat(day(EXAMPLE, MONDAY))
          .containsExactly(
              "07:30-09:00", "18:00-18:15", "18:15-19:00 ★", "20:30-21:00 ★", "22:00-23:30");
    }

    @Test
    void shortFridayFreesTheAfternoon() {
      assertThat(day(EXAMPLE, FRIDAY)).containsExactly("07:30-08:00", "15:00-21:00", "22:00-23:30");
    }

    @Test
    void weekendHasNoWorkAndPrefersTheMorning() {
      assertThat(day(EXAMPLE, SATURDAY))
          .containsExactly("07:30-10:00", "10:00-13:30 ★", "13:30-21:00", "22:00-23:30");
    }

    @Test
    void coversEveryDayOfTheRangeInOrder() {
      List<FreeSlot> week = calculator.freeSlots(EXAMPLE, List.of(), MONDAY, MONDAY.plusDays(7));

      assertThat(week).isSortedAccordingTo((a, b) -> a.start().compareTo(b.start()));
      assertThat(week.getFirst().start()).isEqualTo(at(MONDAY, "07:30"));
      assertThat(week.getLast().end()).isEqualTo(at(SUNDAY, "23:30"));
      assertThat(week).allSatisfy(s -> assertThat(s.end()).isAfter(s.start()));
    }

    @Test
    void endIsExclusive() {
      assertThat(calculator.freeSlots(EXAMPLE, List.of(), MONDAY, MONDAY)).isEmpty();
    }
  }

  @Nested
  class OfficeDays {

    @Test
    void anOfficeDayOverrideAddsTheCommuteBothWays() {
      List<AvailabilityOverride> overrides = List.of(wholeDay(TUESDAY, Effect.OFFICE_DAY));

      assertThat(day(EXAMPLE, TUESDAY, overrides))
          .containsExactly("07:30-08:15", "18:45-21:00 ★", "22:00-23:30");
    }

    @Test
    void anOfficeDayInTheTemplateAddsTheCommuteToo() {
      AvailabilityProfile office =
          EXAMPLE.withWorkDay(
              DayOfWeek.TUESDAY,
              new AvailabilityProfile.WorkDay(
                  AvailabilityProfile.WorkMode.OFFICE, LocalTime.of(9, 0), LocalTime.of(18, 0)));

      assertThat(day(office, TUESDAY))
          .containsExactly("07:30-08:15", "18:45-21:00 ★", "22:00-23:30");
    }

    @Test
    void commuteStaysFreeWhenYouCanStudyOnTheWay() {
      AvailabilityProfile studyOnTrain =
          EXAMPLE.withCommute(new AvailabilityProfile.Commute(45, true));

      assertThat(day(studyOnTrain, TUESDAY, List.of(wholeDay(TUESDAY, Effect.OFFICE_DAY))))
          .containsExactly("07:30-09:00", "18:00-18:15", "18:15-21:00 ★", "22:00-23:30");
    }

    @Test
    void anOfficeDayWithoutWorkChangesNothing() {
      assertThat(day(EXAMPLE, SATURDAY, List.of(wholeDay(SATURDAY, Effect.OFFICE_DAY))))
          .isEqualTo(day(EXAMPLE, SATURDAY));
    }
  }

  @Nested
  class Overrides {

    @Test
    void aBusyDayHasNoFreeTime() {
      assertThat(day(EXAMPLE, MONDAY, List.of(wholeDay(MONDAY, Effect.BUSY)))).isEmpty();
    }

    @Test
    void aBusySlotIsRemoved() {
      List<AvailabilityOverride> plans = List.of(slot(TUESDAY, "19:00", "21:00", Effect.BUSY));

      assertThat(day(EXAMPLE, TUESDAY, plans))
          .containsExactly("07:30-09:00", "18:00-18:15", "18:15-19:00 ★", "22:00-23:30");
    }

    @Test
    void aFreeDayDropsWorkButKeepsSleepAndActivities() {
      List<AvailabilityOverride> holiday = List.of(wholeDay(MONDAY, Effect.FREE));

      assertThat(day(EXAMPLE, MONDAY, holiday))
          .containsExactly("07:30-18:15", "18:15-19:00 ★", "20:30-21:00 ★", "22:00-23:30");
    }

    @Test
    void aFreeSlotOpensTimeTheTemplateHadBusy() {
      List<AvailabilityOverride> dayOffMorning =
          List.of(slot(MONDAY, "09:00", "10:30", Effect.FREE));

      assertThat(day(EXAMPLE, MONDAY, dayOffMorning))
          .containsExactly(
              "07:30-10:30", "18:00-18:15", "18:15-19:00 ★", "20:30-21:00 ★", "22:00-23:30");
    }

    @Test
    void busyWinsOverFreeWhereTheyOverlap() {
      List<AvailabilityOverride> overrides =
          List.of(
              slot(MONDAY, "09:00", "12:00", Effect.FREE),
              slot(MONDAY, "10:00", "11:00", Effect.BUSY));

      assertThat(day(EXAMPLE, MONDAY, overrides)).startsWith("07:30-10:00", "11:00-12:00");
    }

    @Test
    void overridesOfOtherDaysDoNotLeak() {
      assertThat(day(EXAMPLE, MONDAY, List.of(wholeDay(TUESDAY, Effect.BUSY))))
          .isEqualTo(day(EXAMPLE, MONDAY));
    }
  }

  @Nested
  class Preferences {

    @Test
    void noStudyDaysAreEmpty() {
      AvailabilityProfile restOnSunday = EXAMPLE.withNoStudyDays(Set.of(DayOfWeek.SUNDAY));

      assertThat(day(restOnSunday, SUNDAY)).isEmpty();
      assertThat(day(restOnSunday, SATURDAY)).isNotEmpty();
    }

    @Test
    void gapsShorterThanTheMinimumSessionAreDropped() {
      AvailabilityProfile longSessions = EXAMPLE.withMinSessionMinutes(45);

      assertThat(day(longSessions, MONDAY))
          .containsExactly("07:30-09:00", "18:00-18:15", "18:15-19:00 ★", "22:00-23:30");
      assertThat(day(longSessions, FRIDAY)).containsExactly("15:00-21:00", "22:00-23:30");
    }
  }

  @Nested
  class TimeZone {

    @Test
    void followsTheDaylightSavingChange() {
      LocalDate beforeChange = LocalDate.of(2026, 10, 24);
      LocalDate afterChange = LocalDate.of(2026, 10, 25);

      List<FreeSlot> slots =
          calculator.freeSlots(EXAMPLE, List.of(), beforeChange, afterChange.plusDays(1));

      assertThat(slots).extracting(FreeSlot::start).contains(Instant.parse("2026-10-24T05:30:00Z"));
      assertThat(slots).extracting(FreeSlot::start).contains(Instant.parse("2026-10-25T06:30:00Z"));
    }

    @Test
    void reportsTheDurationOfEachSlot() {
      FreeSlot afternoon =
          calculator.freeSlots(EXAMPLE, List.of(), FRIDAY, FRIDAY.plusDays(1)).get(1);

      assertThat(afternoon.duration()).isEqualTo(Duration.ofHours(6));
    }
  }

  private List<String> day(AvailabilityProfile profile, LocalDate date) {
    return day(profile, date, List.of());
  }

  private List<String> day(
      AvailabilityProfile profile, LocalDate date, List<AvailabilityOverride> overrides) {
    DateTimeFormatter hhmm = DateTimeFormatter.ofPattern("HH:mm");
    return calculator.freeSlots(profile, overrides, date, date.plusDays(1)).stream()
        .map(
            s ->
                s.start().atZone(MADRID).format(hhmm)
                    + "-"
                    + s.end().atZone(MADRID).format(hhmm)
                    + (s.preferred() ? " ★" : ""))
        .toList();
  }

  private static Instant at(LocalDate date, String time) {
    return ZonedDateTime.of(date, LocalTime.parse(time), MADRID).toInstant();
  }

  private static AvailabilityOverride wholeDay(LocalDate date, Effect effect) {
    return new AvailabilityOverride(date, null, null, effect);
  }

  private static AvailabilityOverride slot(
      LocalDate date, String start, String end, Effect effect) {
    return new AvailabilityOverride(date, LocalTime.parse(start), LocalTime.parse(end), effect);
  }
}
