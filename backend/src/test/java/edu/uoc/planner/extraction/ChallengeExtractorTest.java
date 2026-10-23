package edu.uoc.planner.extraction;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import edu.uoc.planner.Fixtures;
import edu.uoc.planner.extraction.internal.AnthropicSpecModel;
import edu.uoc.planner.extraction.internal.ExtractionProperties;
import edu.uoc.planner.extraction.internal.SpecValidator;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ChallengeExtractorTest {

  @RegisterExtension
  static WireMockExtension anthropic =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String MESSAGES = "/v1/messages";

  @Test
  void extractsAValidSpecOnTheFirstAttempt() {
    anthropic.stubFor(
        post(urlPathEqualTo(MESSAGES)).willReturn(okJson(message(spec("algebra-r1-spec.json")))));

    ExtractionResult result = extractor("test-key", true).extract(algebraR1());

    assertThat(result).isInstanceOf(ExtractionResult.Extracted.class);
    ChallengeSpec spec = ((ExtractionResult.Extracted) result).spec();
    assertThat(spec.aiPolicy().level()).isEqualTo(ChallengeSpec.AiPolicyLevel.FORBIDDEN);
    assertThat(spec.tasks())
        .extracting(ChallengeSpec.Task::id)
        .containsExactly("t1", "t2", "t3", "t4");
    assertThat(spec.tasks().get(2).kind()).isEqualTo(ChallengeSpec.TaskKind.QUIZ);
    assertThat(result.model()).isEqualTo("claude-opus-5-5");
    anthropic.verify(1, postRequestedFor(urlPathEqualTo(MESSAGES)));
  }

  @Test
  void asksForStructuredOutputWithTheConfiguredModelAndEffort() {
    anthropic.stubFor(
        post(urlPathEqualTo(MESSAGES)).willReturn(okJson(message(spec("algebra-r1-spec.json")))));

    extractor("test-key", true).extract(algebraR1());

    anthropic.verify(
        postRequestedFor(urlPathEqualTo(MESSAGES))
            .withHeader("x-api-key", equalTo("test-key"))
            .withHeader("anthropic-beta", containing("server-side-fallback-2026-07-01"))
            .withRequestBody(matchingJsonPath("$.model", equalTo("claude-opus-5-5")))
            .withRequestBody(matchingJsonPath("$.output_config.effort", equalTo("medium")))
            .withRequestBody(
                matchingJsonPath("$.output_config.format.type", equalTo("json_schema")))
            .withRequestBody(
                matchingJsonPath(
                    "$.output_config.format.schema.additionalProperties", equalTo("false")))
            .withRequestBody(matchingJsonPath("$.fallbacks", equalTo("default")))
            .withRequestBody(notContaining("\"temperature\""))
            .withRequestBody(notContaining("\"thinking\""))
            .withRequestBody(notContaining("\"maximum\""))
            .withRequestBody(notContaining("\"minLength\""))
            .withRequestBody(notContaining("\"pattern\""))
            .withRequestBody(matchingJsonPath("$.system", containing("extractor de requisitos")))
            .withRequestBody(
                matchingJsonPath("$.messages[0].content", containing("assignment:979687")))
            .withRequestBody(
                matchingJsonPath("$.messages[0].content", containing("Curso actual: 94063")))
            .withRequestBody(
                matchingJsonPath(
                    "$.messages[0].content", containing("no està permès l'ús d'eines")))
            .withRequestBody(matchingJsonPath("$.messages[0].content", notContaining("style="))));
  }

  @Test
  void canSkipTheRefusalFallback() {
    anthropic.stubFor(
        post(urlPathEqualTo(MESSAGES)).willReturn(okJson(message(spec("algebra-r1-spec.json")))));

    extractor("test-key", false).extract(algebraR1());

    anthropic.verify(
        postRequestedFor(urlPathEqualTo(MESSAGES))
            .withRequestBody(notContaining("\"fallbacks\""))
            .withoutHeader("anthropic-beta"));
  }

  @Test
  void retriesOnceWithTheValidationErrors() {
    anthropic.stubFor(
        post(urlPathEqualTo(MESSAGES))
            .inScenario("retry")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(okJson(message(spec("algebra-r1-spec-invalid.json"))))
            .willSetStateTo("second"));
    anthropic.stubFor(
        post(urlPathEqualTo(MESSAGES))
            .inScenario("retry")
            .whenScenarioStateIs("second")
            .willReturn(okJson(message(spec("algebra-r1-spec.json")))));

    ExtractionResult result = extractor("test-key", true).extract(algebraR1());

    assertThat(result).isInstanceOf(ExtractionResult.Extracted.class);
    anthropic.verify(2, postRequestedFor(urlPathEqualTo(MESSAGES)));
    anthropic.verify(
        postRequestedFor(urlPathEqualTo(MESSAGES))
            .withRequestBody(matchingJsonPath("$.messages.length()", equalTo("1")))
            .withRequestBody(
                matchingJsonPath("$.messages[0].content", containing("assignment:123456")))
            .withRequestBody(matchingJsonPath("$.messages[0].content", containing("solution"))));
  }

  @Test
  void needsReviewWhenTheRetryIsStillInvalid() {
    anthropic.stubFor(
        post(urlPathEqualTo(MESSAGES))
            .willReturn(okJson(message(spec("algebra-r1-spec-invalid.json")))));

    ExtractionResult result = extractor("test-key", true).extract(algebraR1());

    assertThat(result).isInstanceOf(ExtractionResult.NeedsReview.class);
    assertThat(((ExtractionResult.NeedsReview) result).errors())
        .anyMatch(e -> e.contains("assignment:123456"));
    anthropic.verify(2, postRequestedFor(urlPathEqualTo(MESSAGES)));
  }

  @Test
  void needsReviewWithoutRetryingWhenTheModelRefuses() {
    anthropic.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(okJson(refusal())));

    ExtractionResult result = extractor("test-key", true).extract(algebraR1());

    assertThat(result).isInstanceOf(ExtractionResult.NeedsReview.class);
    assertThat(((ExtractionResult.NeedsReview) result).errors())
        .anyMatch(e -> e.contains("refusal"));
    anthropic.verify(1, postRequestedFor(urlPathEqualTo(MESSAGES)));
  }

  @Test
  void treatsATruncatedAnswerAsInvalid() {
    anthropic.stubFor(
        post(urlPathEqualTo(MESSAGES))
            .willReturn(okJson(message("{\"schemaVersion\": 1, \"tasks\": [", "max_tokens"))));

    ExtractionResult result = extractor("test-key", true).extract(algebraR1());

    assertThat(result).isInstanceOf(ExtractionResult.NeedsReview.class);
    assertThat(((ExtractionResult.NeedsReview) result).errors())
        .anyMatch(e -> e.contains("max_tokens"));
  }

  @Test
  void isUnavailableWhenTheApiFails() {
    anthropic.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(aResponse().withStatus(529)));

    assertThatThrownBy(() -> extractor("test-key", true).extract(algebraR1()))
        .isInstanceOf(ExtractionUnavailableException.class);
  }

  @Test
  void explainsWhyTheApiRefusedTheRequest() {
    anthropic.stubFor(
        post(urlPathEqualTo(MESSAGES))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\","
                            + "\"message\":\"Your credit balance is too low to access the Anthropic API.\"}}")));

    assertThatThrownBy(() -> extractor("test-key", true).extract(algebraR1()))
        .isInstanceOf(ExtractionUnavailableException.class)
        .hasMessageContaining("400")
        .hasMessageContaining("Your credit balance is too low")
        .hasMessageNotContaining("test-key");
  }

  @Test
  void isUnavailableWithoutCallingTheApiWhenThereIsNoKey() {
    assertThatThrownBy(() -> extractor("", true).extract(algebraR1()))
        .isInstanceOf(ExtractionUnavailableException.class)
        .hasMessageContaining("ANTHROPIC_API_KEY");
    anthropic.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void hashesTheSourceSoUnchangedStatementsAreNotExtractedAgain() {
    anthropic.stubFor(
        post(urlPathEqualTo(MESSAGES)).willReturn(okJson(message(spec("algebra-r1-spec.json")))));
    ChallengeExtractor extractor = extractor("test-key", true);
    ExtractionInput input = algebraR1();
    ExtractionInput changed =
        new ExtractionInput(
            input.courseId(),
            input.challengeTitle(),
            input.statementHtml() + "<p>Nova data</p>",
            input.approachHtml(),
            input.studyGuideHtml(),
            input.rubric(),
            input.deliverables());

    assertThat(extractor.sourceHash(input)).isEqualTo(extractor.sourceHash(algebraR1()));
    assertThat(extractor.sourceHash(changed)).isNotEqualTo(extractor.sourceHash(input));
    assertThat(extractor.extract(input).sourceHash()).isEqualTo(extractor.sourceHash(input));
  }

  private static ChallengeExtractor extractor(String apiKey, boolean refusalFallback) {
    ExtractionProperties properties =
        new ExtractionProperties(
            apiKey,
            URI.create(anthropic.baseUrl()),
            "claude-opus-5-5",
            "medium",
            16000,
            Duration.ofSeconds(10),
            0,
            refusalFallback);
    return new ChallengeExtractor(new AnthropicSpecModel(properties), new SpecValidator());
  }

  static ExtractionInput algebraR1() {
    return new ExtractionInput(
        94063,
        "Repte 1. T'atreveixes a resoldre una equació de segon grau?",
        pageBody("algebra-page-enunciat-r1.json"),
        null,
        null,
        null,
        List.of(
            new ExtractionInput.Deliverable(
                "assignment:979687",
                "QÜESTIONARI 1 - Setmana 2",
                Instant.parse("2026-10-02T21:59:59Z"),
                new BigDecimal("15.0"),
                true),
            new ExtractionInput.Deliverable(
                "assignment:979674",
                "Enregistraments R1 -- Vídeo de comprovació identitat i autoria",
                Instant.parse("2026-10-11T21:59:59Z"),
                BigDecimal.ZERO,
                false)));
  }

  static String pageBody(String fixture) {
    try {
      return MAPPER.readTree(Fixtures.json("canvas/" + fixture)).get("body").asText();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String spec(String fixture) {
    return Fixtures.json("claude/" + fixture);
  }

  /** Messages API response with an (omitted) thinking block followed by the JSON text. */
  private static String message(String text) {
    return message(text, "end_turn");
  }

  private static String message(String text, String stopReason) {
    ObjectNode message = envelope(stopReason);
    message
        .withArray("content")
        .addObject()
        .put("type", "thinking")
        .put("thinking", "")
        .put("signature", "c2ln");
    message.withArray("content").addObject().put("type", "text").put("text", text);
    return message.toString();
  }

  private static String refusal() {
    ObjectNode message = envelope("refusal");
    message.withArray("content");
    message
        .putObject("stop_details")
        .put("type", "refusal")
        .put("category", "cyber")
        .put("explanation", "test");
    return message.toString();
  }

  private static ObjectNode envelope(String stopReason) {
    ObjectNode message =
        MAPPER
            .createObjectNode()
            .put("id", "msg_test")
            .put("type", "message")
            .put("role", "assistant")
            .put("model", "claude-opus-5-5")
            .put("stop_reason", stopReason)
            .putNull("stop_sequence");
    message.putObject("usage").put("input_tokens", 4100).put("output_tokens", 900);
    return message;
  }
}
