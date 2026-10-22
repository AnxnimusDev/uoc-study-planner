package edu.uoc.planner.availability.internal;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code planner.availability.*}.
 *
 * @param file the student's profile; relative to {@code backend/}, where {@code make run} starts
 */
@ConfigurationProperties("planner.availability")
public record AvailabilityProperties(@DefaultValue("../config/availability.yml") Path file) {}
