package edu.uoc.planner.availability.internal;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code planner.availability.*}.
 *
 * @param file the student's profile; relative to {@code backend/}, where {@code make run} starts.
 *     Bound as text: with the web server on, Spring would resolve a {@link Path} as a resource of
 *     the servlet context, and "../" falls outside it.
 */
@ConfigurationProperties("planner.availability")
public record AvailabilityProperties(@DefaultValue("../config/availability.yml") String file) {

  public Path path() {
    return Path.of(file);
  }
}
