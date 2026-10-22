package edu.uoc.planner.planning.internal;

import edu.uoc.planner.availability.FreeSlot;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What is still free while the plan is built: free windows, minutes studied per day and courses per
 * day. Copied for each attempt, so a failed attempt leaves no trace.
 */
final class Capacity {

  /** Pause after every session; reserved but not counted as study. */
  static final Duration BREAK = Duration.ofMinutes(15);

  record Window(Instant start, Instant end, boolean preferred) {}

  /** Where a session can go. {@code minutes} is already capped by every limit. */
  record Spot(Instant start, long minutes) {}

  private final ZoneId zone;
  private final List<Window> free;
  private final Map<LocalDate, Long> studied;
  private final Map<LocalDate, Set<String>> courses;

  private Capacity(
      ZoneId zone,
      List<Window> free,
      Map<LocalDate, Long> studied,
      Map<LocalDate, Set<String>> courses) {
    this.zone = zone;
    this.free = free;
    this.studied = studied;
    this.courses = courses;
  }

  static Capacity of(List<FreeSlot> slots, Instant from, Instant to, ZoneId zone) {
    List<Window> windows = new ArrayList<>();
    for (FreeSlot slot : slots) {
      Instant start = slot.start().isBefore(from) ? from : slot.start();
      Instant end = slot.end().isAfter(to) ? to : slot.end();
      if (start.isBefore(end)) {
        windows.add(new Window(start, end, slot.preferred()));
      }
    }
    windows.sort(Comparator.comparing(Window::start));
    return new Capacity(zone, windows, new HashMap<>(), new HashMap<>());
  }

  Capacity copy() {
    Map<LocalDate, Set<String>> coursesCopy = new HashMap<>();
    courses.forEach((day, set) -> coursesCopy.put(day, new HashSet<>(set)));
    return new Capacity(zone, new ArrayList<>(free), new HashMap<>(studied), coursesCopy);
  }

  /**
   * Earliest place for a session between {@code minMinutes} and {@code maxMinutes}.
   *
   * @param maxDailyMinutes daily study limit
   * @param courseLimit days that already have two other courses are skipped
   */
  Optional<Spot> find(
      Instant earliest,
      Instant latest,
      long minMinutes,
      long maxMinutes,
      boolean preferredOnly,
      boolean courseLimit,
      String course,
      long maxDailyMinutes) {
    for (Window window : windows(preferredOnly)) {
      if (!window.end().isAfter(earliest)) {
        continue;
      }
      if (!window.start().isBefore(latest)) {
        break;
      }
      Instant start = window.start().isBefore(earliest) ? earliest : window.start();
      LocalDate day = day(start);
      Instant end = min(min(window.end(), latest), endOfDay(day));
      if (courseLimit) {
        Set<String> today = courses.getOrDefault(day, Set.of());
        if (today.size() >= 2 && !today.contains(course)) {
          continue;
        }
      }
      long dayLeft = maxDailyMinutes - studied.getOrDefault(day, 0L);
      long minutes =
          Math.min(Math.min(maxMinutes, Duration.between(start, end).toMinutes()), dayLeft);
      if (minutes >= minMinutes && minutes > 0) {
        return Optional.of(new Spot(start, minutes));
      }
    }
    return Optional.empty();
  }

  /** Takes a session (and the break after it) out of the free time. */
  void reserve(Instant start, Instant end, String course) {
    LocalDate day = day(start);
    studied.merge(day, Duration.between(start, end).toMinutes(), Long::sum);
    courses.computeIfAbsent(day, d -> new HashSet<>()).add(course);
    subtract(start, end.plus(BREAK));
  }

  private void subtract(Instant start, Instant end) {
    List<Window> remaining = new ArrayList<>();
    for (Window w : free) {
      if (!w.end().isAfter(start) || !w.start().isBefore(end)) {
        remaining.add(w);
        continue;
      }
      if (w.start().isBefore(start)) {
        remaining.add(new Window(w.start(), start, w.preferred()));
      }
      if (w.end().isAfter(end)) {
        remaining.add(new Window(end, w.end(), w.preferred()));
      }
    }
    free.clear();
    free.addAll(remaining);
  }

  /** Preferred windows only, or every window with touching ones joined into one. */
  private List<Window> windows(boolean preferredOnly) {
    if (preferredOnly) {
      return free.stream().filter(Window::preferred).toList();
    }
    List<Window> merged = new ArrayList<>();
    for (Window w : free) {
      if (!merged.isEmpty() && merged.getLast().end().equals(w.start())) {
        Window last = merged.removeLast();
        merged.add(new Window(last.start(), w.end(), last.preferred() && w.preferred()));
      } else {
        merged.add(w);
      }
    }
    return merged;
  }

  private LocalDate day(Instant instant) {
    return instant.atZone(zone).toLocalDate();
  }

  private Instant endOfDay(LocalDate day) {
    return day.plusDays(1).atStartOfDay(zone).toInstant();
  }

  private static Instant min(Instant a, Instant b) {
    return a.isBefore(b) ? a : b;
  }
}
