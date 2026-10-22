package edu.uoc.planner.extraction.internal;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code planner.extraction.*} (ADR 0006).
 *
 * @param apiKey {@code ANTHROPIC_API_KEY}; extraction is unavailable without it
 * @param baseUrl tests point it at WireMock
 * @param effort {@code low}, {@code medium}, {@code high}, {@code xhigh} or {@code max}
 * @param maxRetries SDK retries on 429, 5xx and network errors
 * @param refusalFallback let the API answer with another model when this one refuses
 */
@ConfigurationProperties("planner.extraction")
public record ExtractionProperties(
    String apiKey,
    @DefaultValue("https://api.anthropic.com") URI baseUrl,
    @DefaultValue("claude-opus-5-5") String model,
    @DefaultValue("medium") String effort,
    @DefaultValue("16000") long maxTokens,
    @DefaultValue("5m") Duration timeout,
    @DefaultValue("2") int maxRetries,
    @DefaultValue("true") boolean refusalFallback) {}
