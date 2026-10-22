package edu.uoc.planner.availability.internal;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Disjoint, sorted ranges of minutes within one day ({@code [start, end)}, 0–1440). Mutable on
 * purpose: one instance per day being computed.
 */
public final class DayIntervals {

  public static final int END_OF_DAY = 24 * 60;

  private final List<int[]> ranges = new ArrayList<>();

  public static DayIntervals between(LocalTime start, LocalTime end) {
    DayIntervals intervals = new DayIntervals();
    intervals.add(minutes(start), minutes(end));
    return intervals;
  }

  public static int minutes(LocalTime time) {
    return time.getHour() * 60 + time.getMinute();
  }

  public void add(int start, int end) {
    int from = clamp(start);
    int to = clamp(end);
    if (to <= from) {
      return;
    }
    List<int[]> merged = new ArrayList<>();
    for (int[] r : ranges) {
      if (r[1] < from || r[0] > to) {
        merged.add(r);
      } else {
        from = Math.min(from, r[0]);
        to = Math.max(to, r[1]);
      }
    }
    merged.add(new int[] {from, to});
    merged.sort((a, b) -> Integer.compare(a[0], b[0]));
    ranges.clear();
    ranges.addAll(merged);
  }

  public void subtract(int start, int end) {
    int from = clamp(start);
    int to = clamp(end);
    if (to <= from) {
      return;
    }
    List<int[]> remaining = new ArrayList<>();
    for (int[] r : ranges) {
      if (r[1] <= from || r[0] >= to) {
        remaining.add(r);
        continue;
      }
      if (r[0] < from) {
        remaining.add(new int[] {r[0], from});
      }
      if (r[1] > to) {
        remaining.add(new int[] {to, r[1]});
      }
    }
    ranges.clear();
    ranges.addAll(remaining);
  }

  public void subtract(LocalTime start, LocalTime end) {
    subtract(minutes(start), minutes(end));
  }

  public void add(LocalTime start, LocalTime end) {
    add(minutes(start), minutes(end));
  }

  /** Drops ranges shorter than {@code minutes}. */
  public void dropShorterThan(int minutes) {
    ranges.removeIf(r -> r[1] - r[0] < minutes);
  }

  /** Copies of the ranges, as {@code [start, end]} minute pairs. */
  public List<int[]> ranges() {
    return ranges.stream().map(int[]::clone).toList();
  }

  private static int clamp(int minute) {
    return Math.max(0, Math.min(END_OF_DAY, minute));
  }
}
