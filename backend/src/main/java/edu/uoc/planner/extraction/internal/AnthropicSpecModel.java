package edu.uoc.planner.extraction.internal;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlock;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.uoc.planner.extraction.ExtractionUnavailableException;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link SpecModel} on the official Anthropic Java SDK (ADR 0006): structured output against the
 * ChallengeSpec schema, configured effort, no temperature. The client is built on first use, so the
 * application starts without an API key.
 */
public final class AnthropicSpecModel implements SpecModel {

  private static final Logger log = LoggerFactory.getLogger(AnthropicSpecModel.class);

  private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

  private final ExtractionProperties properties;
  private final String systemPrompt;
  private final Map<String, JsonValue> apiSchema;
  private volatile AnthropicClient client;

  public AnthropicSpecModel(ExtractionProperties properties) {
    this.properties = properties;
    this.systemPrompt = ExtractionPrompts.system();
    Map<String, Object> schema =
        new ObjectMapper().convertValue(SpecSchema.forApi(), new TypeReference<>() {});
    this.apiSchema =
        schema.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> JsonValue.from(e.getValue())));
  }

  @Override
  public ModelReply complete(String userMessage) {
    MessageCreateParams.Builder params =
        MessageCreateParams.builder()
            .model(properties.model())
            .maxTokens(properties.maxTokens())
            .system(systemPrompt)
            .addUserMessage(userMessage)
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.of(properties.effort()))
                    .format(
                        JsonOutputFormat.builder()
                            .schema(
                                JsonOutputFormat.Schema.builder()
                                    .putAllAdditionalProperties(apiSchema)
                                    .build())
                            .build())
                    .build());
    if (properties.refusalFallback()) {
      params
          .putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
          .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
    }
    Message message;
    try {
      message = client().messages().create(params.build());
    } catch (AnthropicServiceException e) {
      throw new ExtractionUnavailableException("Claude API answered " + e.statusCode(), e);
    } catch (AnthropicException e) {
      throw new ExtractionUnavailableException("Cannot reach the Claude API: " + e.getMessage(), e);
    }
    String text =
        message.content().stream()
            .flatMap(block -> block.text().stream())
            .map(TextBlock::text)
            .collect(Collectors.joining());
    String stopReason = message.stopReason().map(Object::toString).orElse("");
    String category =
        message.stopDetails().flatMap(d -> d.category()).map(Object::toString).orElse(null);
    log.info("Extraction answered by {} (stop_reason {})", message.model(), stopReason);
    return new ModelReply(text, stopReason, message.model().toString(), category);
  }

  private AnthropicClient client() {
    AnthropicClient current = client;
    if (current == null) {
      if (properties.apiKey() == null || properties.apiKey().isBlank()) {
        throw new ExtractionUnavailableException(
            "ANTHROPIC_API_KEY is not set: requirement extraction is disabled");
      }
      synchronized (this) {
        if (client == null) {
          client =
              AnthropicOkHttpClient.builder()
                  .apiKey(properties.apiKey())
                  .baseUrl(properties.baseUrl().toString())
                  .timeout(properties.timeout())
                  .maxRetries(properties.maxRetries())
                  .build();
        }
        current = client;
      }
    }
    return current;
  }
}
