package edu.uoc.planner.availability.internal;

import edu.uoc.planner.availability.AvailabilityConfigException;
import edu.uoc.planner.availability.AvailabilityProfile;
import edu.uoc.planner.availability.AvailabilityProfile.Activity;
import edu.uoc.planner.availability.AvailabilityProfile.Commute;
import edu.uoc.planner.availability.AvailabilityProfile.PreferredSlot;
import edu.uoc.planner.availability.AvailabilityProfile.TimeRange;
import edu.uoc.planner.availability.AvailabilityProfile.WorkDay;
import edu.uoc.planner.availability.AvailabilityProfile.WorkMode;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Reads {@code config/availability.yml} (format: {@code config/availability.example.yml}) with the
 * SnakeYAML that Spring Boot already ships. Every inconsistency fails with a message in Spanish
 * that names the field, because the student edits this file by hand.
 */
public final class AvailabilityProfileLoader {

  private AvailabilityProfileLoader() {}

  public static AvailabilityProfile load(Path file) {
    Map<String, Object> root;
    try (Reader reader = Files.newBufferedReader(file)) {
      Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
      root = parsed == null ? Map.of() : map(parsed, "raíz del fichero");
    } catch (NoSuchFileException e) {
      throw new AvailabilityConfigException(
          "No existe "
              + file
              + ". Copia config/availability.example.yml a config/availability.yml y ajústalo.");
    } catch (IOException | YAMLException e) {
      throw new AvailabilityConfigException("No se puede leer " + file + ": " + e.getMessage(), e);
    }
    return parse(root);
  }

  private static AvailabilityProfile parse(Map<String, Object> root) {
    ZoneId zone = zone(root.getOrDefault("timezone", "Europe/Madrid"));

    Map<String, Object> work = optionalMap(root, "work");
    Map<DayOfWeek, WorkDay> workDays = new EnumMap<>(DayOfWeek.class);
    optionalMap(work, "days")
        .forEach(
            (day, value) -> {
              Map<String, Object> entry = map(value, "work.days." + day);
              String field = "work.days." + day;
              LocalTime start = time(entry.get("start"), field + ".start");
              LocalTime end = time(entry.get("end"), field + ".end");
              requireOrder(start, end, field);
              workDays.put(
                  day(day, field), new WorkDay(mode(entry.get("mode"), field), start, end));
            });
    TimeRange lunch = null;
    if (work.get("lunch") != null) {
      Map<String, Object> entry = map(work.get("lunch"), "work.lunch");
      lunch =
          new TimeRange(
              time(entry.get("start"), "work.lunch.start"),
              time(entry.get("end"), "work.lunch.end"));
      requireOrder(lunch.start(), lunch.end(), "work.lunch");
    }

    Map<String, Object> commuteEntry = optionalMap(root, "commute");
    int commuteMinutes =
        integer(commuteEntry.getOrDefault("minutesEachWay", 0), "commute.minutesEachWay");
    if (commuteMinutes < 0) {
      throw new AvailabilityConfigException("commute.minutesEachWay no puede ser negativo");
    }
    Commute commute =
        new Commute(commuteMinutes, Boolean.TRUE.equals(commuteEntry.get("canStudyDuringCommute")));

    Map<String, Object> sleep = optionalMap(root, "sleep");
    LocalTime wakeUp = time(sleep.getOrDefault("wakeUp", "07:00"), "sleep.wakeUp");
    LocalTime bedtime = time(sleep.getOrDefault("bedtime", "23:00"), "sleep.bedtime");
    if (!bedtime.isAfter(wakeUp)) {
      throw new AvailabilityConfigException(
          "sleep.bedtime ("
              + bedtime
              + ") debe ser posterior a sleep.wakeUp ("
              + wakeUp
              + "): acostarse después de medianoche aún no está soportado");
    }

    List<Activity> activities = new ArrayList<>();
    for (Object value : list(root.get("fixedActivities"), "fixedActivities")) {
      Map<String, Object> entry = map(value, "fixedActivities");
      String label = String.valueOf(entry.getOrDefault("label", "sin nombre"));
      String field = "fixedActivities \"" + label + "\"";
      LocalTime start = time(entry.get("start"), field + ".start");
      LocalTime end = time(entry.get("end"), field + ".end");
      requireOrder(start, end, field);
      activities.add(new Activity(label, days(entry.get("days"), field), start, end));
    }

    Map<String, Object> study = optionalMap(root, "studyPreferences");
    List<PreferredSlot> preferred = new ArrayList<>();
    for (Object value : list(study.get("preferredSlots"), "studyPreferences.preferredSlots")) {
      Map<String, Object> entry = map(value, "studyPreferences.preferredSlots");
      String field = "studyPreferences.preferredSlots";
      LocalTime start = time(entry.get("start"), field + ".start");
      LocalTime end = time(entry.get("end"), field + ".end");
      requireOrder(start, end, field);
      preferred.add(new PreferredSlot(days(entry.get("days"), field), start, end));
    }
    int minSession = integer(study.getOrDefault("minSessionMinutes", 30), "minSessionMinutes");
    int maxSession = integer(study.getOrDefault("maxSessionMinutes", 120), "maxSessionMinutes");
    double maxDaily = number(study.getOrDefault("maxDailyHours", 3), "maxDailyHours");
    if (minSession <= 0 || minSession > maxSession) {
      throw new AvailabilityConfigException(
          "studyPreferences.minSessionMinutes ("
              + minSession
              + ") debe ser mayor que 0 y no superar maxSessionMinutes ("
              + maxSession
              + ")");
    }
    if (maxDaily <= 0 || maxDaily * 60 < minSession) {
      throw new AvailabilityConfigException(
          "studyPreferences.maxDailyHours debe dejar al menos una sesión mínima al día");
    }
    Map<DayOfWeek, Double> maxDailyPerDay = new EnumMap<>(DayOfWeek.class);
    optionalMap(study, "maxDailyHoursPerDay")
        .forEach(
            (day, value) -> {
              String field = "studyPreferences.maxDailyHoursPerDay." + day;
              double hours = number(value, field);
              if (hours <= 0 || hours * 60 < minSession) {
                throw new AvailabilityConfigException(
                    field + " debe dejar al menos una sesión mínima (" + minSession + " min)");
              }
              maxDailyPerDay.put(day(day, field), hours);
            });
    Set<DayOfWeek> noStudy = days(study.getOrDefault("noStudyDays", List.of()), "noStudyDays");

    Map<String, Object> deadlines = optionalMap(root, "deadlines");
    int defaultMargin =
        integer(deadlines.getOrDefault("defaultMarginDays", 1), "defaultMarginDays");
    Map<String, Integer> perCourse = new LinkedHashMap<>();
    optionalMap(deadlines, "perCourse")
        .forEach((code, days) -> perCourse.put(code, integer(days, "deadlines.perCourse." + code)));

    List<AvailabilityProfile.WeeklyStudy> weekly = new ArrayList<>();
    for (Object value : list(root.get("weeklyStudy"), "weeklyStudy")) {
      Map<String, Object> entry = map(value, "weeklyStudy");
      Object course = entry.get("course");
      if (!(course instanceof String code) || code.isBlank()) {
        throw new AvailabilityConfigException(
            "weeklyStudy: cada entrada necesita course con el código de la asignatura, como \"20.043\"");
      }
      int minutes = integer(entry.get("minutes"), "weeklyStudy " + code + ".minutes");
      if (minutes <= 0 || minutes > 600) {
        throw new AvailabilityConfigException(
            "weeklyStudy "
                + code
                + ".minutes debe estar entre 1 y 600 (se ha leído "
                + minutes
                + ")");
      }
      Object title = entry.get("title");
      weekly.add(
          new AvailabilityProfile.WeeklyStudy(
              code.strip(), minutes, title == null ? null : String.valueOf(title).strip()));
    }

    return new AvailabilityProfile(
        zone,
        workDays,
        lunch,
        commute,
        wakeUp,
        bedtime,
        activities,
        preferred,
        minSession,
        maxSession,
        maxDaily,
        maxDailyPerDay,
        noStudy,
        defaultMargin,
        perCourse,
        weekly);
  }

  private static ZoneId zone(Object value) {
    try {
      return ZoneId.of(String.valueOf(value));
    } catch (DateTimeException e) {
      throw new AvailabilityConfigException("timezone no válida: " + value);
    }
  }

  private static LocalTime time(Object value, String field) {
    if (!(value instanceof String text)) {
      throw new AvailabilityConfigException(
          field + " debe ser una hora entre comillas, como \"09:00\" (se ha leído: " + value + ")");
    }
    try {
      return LocalTime.parse(text);
    } catch (DateTimeParseException e) {
      throw new AvailabilityConfigException(field + " no es una hora HH:mm: " + text);
    }
  }

  private static void requireOrder(LocalTime start, LocalTime end, String field) {
    if (!end.isAfter(start)) {
      throw new AvailabilityConfigException(
          field + " termina (" + end + ") antes de empezar (" + start + ")");
    }
  }

  private static DayOfWeek day(Object value, String field) {
    try {
      return DayOfWeek.valueOf(String.valueOf(value).strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new AvailabilityConfigException(
          field + ": día desconocido " + value + " (usa MONDAY … SUNDAY)");
    }
  }

  private static Set<DayOfWeek> days(Object value, String field) {
    Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
    for (Object day : list(value, field)) {
      days.add(day(day, field));
    }
    return days;
  }

  private static WorkMode mode(Object value, String field) {
    try {
      return WorkMode.valueOf(String.valueOf(value).strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new AvailabilityConfigException(field + ".mode debe ser remote u office: " + value);
    }
  }

  private static int integer(Object value, String field) {
    if (value instanceof Integer i) {
      return i;
    }
    throw new AvailabilityConfigException(field + " debe ser un número entero: " + value);
  }

  private static double number(Object value, String field) {
    if (value instanceof Number n) {
      return n.doubleValue();
    }
    throw new AvailabilityConfigException(field + " debe ser un número: " + value);
  }

  private static List<?> list(Object value, String field) {
    if (value == null) {
      return List.of();
    }
    if (value instanceof List<?> l) {
      return l;
    }
    throw new AvailabilityConfigException(field + " debe ser una lista");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value, String field) {
    if (value instanceof Map<?, ?> m) {
      Map<String, Object> result = new LinkedHashMap<>();
      ((Map<Object, Object>) m).forEach((k, v) -> result.put(String.valueOf(k), v));
      return result;
    }
    throw new AvailabilityConfigException(field + " debe ser un bloque clave: valor");
  }

  private static Map<String, Object> optionalMap(Map<String, Object> parent, String key) {
    Object value = parent.get(key);
    return value == null ? Map.of() : map(value, key);
  }
}
