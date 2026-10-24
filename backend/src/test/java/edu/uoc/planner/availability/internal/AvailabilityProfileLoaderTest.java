package edu.uoc.planner.availability.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.uoc.planner.availability.AvailabilityConfigException;
import edu.uoc.planner.availability.AvailabilityProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AvailabilityProfileLoaderTest {

  private static final Path EXAMPLE = Path.of("../config/availability.example.yml");

  @TempDir Path dir;

  @Test
  void readsTheExampleConfiguration() {
    AvailabilityProfile profile = AvailabilityProfileLoader.load(EXAMPLE);

    assertThat(profile.zone()).isEqualTo(ZoneId.of("Europe/Madrid"));
    assertThat(profile.workDays())
        .containsOnlyKeys(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY);
    assertThat(profile.workDays().get(DayOfWeek.FRIDAY).end()).isEqualTo(LocalTime.of(15, 0));
    assertThat(profile.workDays().get(DayOfWeek.MONDAY).mode())
        .isEqualTo(AvailabilityProfile.WorkMode.REMOTE);
    assertThat(profile.commute().minutesEachWay()).isEqualTo(45);
    assertThat(profile.wakeUp()).isEqualTo(LocalTime.of(7, 30));
    assertThat(profile.bedtime()).isEqualTo(LocalTime.of(23, 30));
    assertThat(profile.fixedActivities())
        .extracting(AvailabilityProfile.Activity::label)
        .containsExactly("Gimnasio", "Cena");
    assertThat(profile.preferredSlots()).hasSize(2);
    assertThat(profile.minSessionMinutes()).isEqualTo(30);
    assertThat(profile.maxSessionMinutes()).isEqualTo(120);
    assertThat(profile.maxDailyHours()).isEqualTo(3);
    assertThat(profile.noStudyDays()).isEmpty();
  }

  @Test
  void resolvesTheDeadlineMarginPerCourse() throws IOException {
    Path config =
        write(
            Files.readString(EXAMPLE)
                .replace(
                    "    # \"05.557\": 2   # código de asignatura → días de margen",
                    "    \"05.557\": 2"));

    AvailabilityProfile profile = AvailabilityProfileLoader.load(config);

    assertThat(profile.marginDays("05.557")).isEqualTo(2);
    assertThat(profile.marginDays("05.590")).isEqualTo(1);
  }

  @Test
  void hasNoWeeklyStudyUnlessConfigured() {
    assertThat(AvailabilityProfileLoader.load(EXAMPLE).weeklyStudy()).isEmpty();
  }

  @Test
  void readsTheWeeklyStudyTime() throws IOException {
    Path config =
        write(
            Files.readString(EXAMPLE)
                + """

                weeklyStudy:
                  - { course: "20.043", minutes: 60, title: "Laboratori de prototipat" }
                  - { course: "05.658", minutes: 30 }
                """);

    AvailabilityProfile profile = AvailabilityProfileLoader.load(config);

    assertThat(profile.weeklyStudy())
        .containsExactly(
            new AvailabilityProfile.WeeklyStudy("20.043", 60, "Laboratori de prototipat"),
            new AvailabilityProfile.WeeklyStudy("05.658", 30, null));
  }

  @Test
  void rejectsWeeklyStudyWithoutTime() throws IOException {
    Path config =
        write(
            Files.readString(EXAMPLE) + "\nweeklyStudy:\n  - { course: \"20.043\", minutes: 0 }\n");

    assertThatThrownBy(() -> AvailabilityProfileLoader.load(config))
        .isInstanceOf(AvailabilityConfigException.class)
        .hasMessageContaining("weeklyStudy");
  }

  @Test
  void rejectsWeeklyStudyWithoutCourse() throws IOException {
    Path config = write(Files.readString(EXAMPLE) + "\nweeklyStudy:\n  - { minutes: 60 }\n");

    assertThatThrownBy(() -> AvailabilityProfileLoader.load(config))
        .isInstanceOf(AvailabilityConfigException.class)
        .hasMessageContaining("course");
  }

  @Test
  void explainsHowToCreateAMissingFile() {
    assertThatThrownBy(() -> AvailabilityProfileLoader.load(dir.resolve("availability.yml")))
        .isInstanceOf(AvailabilityConfigException.class)
        .hasMessageContaining("availability.example.yml");
  }

  @Test
  void rejectsABedtimeBeforeWakingUp() throws IOException {
    Path config =
        write(Files.readString(EXAMPLE).replace("bedtime: \"23:30\"", "bedtime: \"06:00\""));

    assertThatThrownBy(() -> AvailabilityProfileLoader.load(config))
        .isInstanceOf(AvailabilityConfigException.class)
        .hasMessageContaining("bedtime");
  }

  @Test
  void rejectsASlotThatEndsBeforeItStarts() throws IOException {
    Path config =
        write(
            Files.readString(EXAMPLE)
                .replace("start: \"19:00\", end: \"20:30\"", "start: \"20:30\", end: \"19:00\""));

    assertThatThrownBy(() -> AvailabilityProfileLoader.load(config))
        .isInstanceOf(AvailabilityConfigException.class)
        .hasMessageContaining("Gimnasio");
  }

  @Test
  void rejectsAnUnknownDay() throws IOException {
    Path config =
        write(Files.readString(EXAMPLE).replace("days: [MONDAY, WEDNESDAY]", "days: [LUNES]"));

    assertThatThrownBy(() -> AvailabilityProfileLoader.load(config))
        .isInstanceOf(AvailabilityConfigException.class)
        .hasMessageContaining("LUNES");
  }

  @Test
  void rejectsSessionLimitsThatContradictEachOther() throws IOException {
    Path config =
        write(Files.readString(EXAMPLE).replace("minSessionMinutes: 30", "minSessionMinutes: 180"));

    assertThatThrownBy(() -> AvailabilityProfileLoader.load(config))
        .isInstanceOf(AvailabilityConfigException.class)
        .hasMessageContaining("minSessionMinutes");
  }

  private Path write(String yaml) throws IOException {
    Path file = dir.resolve("availability.yml");
    Files.writeString(file, yaml);
    return file;
  }
}
