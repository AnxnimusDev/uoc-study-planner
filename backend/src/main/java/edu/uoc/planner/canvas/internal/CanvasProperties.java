package edu.uoc.planner.canvas.internal;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * {@code planner.canvas.*}. Tests point {@code baseUrl} at WireMock.
 *
 * @param requestsPerSecond rate limit towards Canvas
 * @param maxAttempts attempts per request on 429, 5xx or network errors, including the first one
 * @param initialBackoff wait before the first retry; doubles on each attempt
 * @param maxResponseSize largest body accepted (page HTML can be heavy)
 */
@ConfigurationProperties("planner.canvas")
record CanvasProperties(
    @DefaultValue("https://aula.uoc.edu") URI baseUrl,
    @DefaultValue("2") int requestsPerSecond,
    @DefaultValue("4") int maxAttempts,
    @DefaultValue("1s") Duration initialBackoff,
    @DefaultValue("30s") Duration timeout,
    @DefaultValue("10MB") DataSize maxResponseSize) {}
