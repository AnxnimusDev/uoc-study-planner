package edu.uoc.planner.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import edu.uoc.planner.extraction.internal.AnthropicSpecModel;
import edu.uoc.planner.extraction.internal.ExtractionProperties;
import edu.uoc.planner.extraction.internal.SpecValidator;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Calls the real Claude API (costs money). Left out of {@code mvn verify}: {@code ./mvnw test
 * -Dgroups=live -Dsurefire.excludedGroups= -Dtest=ChallengeExtractorLiveTest}.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ChallengeExtractorLiveTest {

  @Test
  void extractsAlgebraR1() {
    ExtractionProperties properties =
        new ExtractionProperties(
            System.getenv("ANTHROPIC_API_KEY"),
            URI.create("https://api.anthropic.com"),
            "claude-opus-5-5",
            "medium",
            16000,
            Duration.ofMinutes(5),
            2,
            true);
    ChallengeExtractor extractor =
        new ChallengeExtractor(new AnthropicSpecModel(properties), new SpecValidator());

    ExtractionResult result = extractor.extract(ChallengeExtractorTest.algebraR1());

    assertThat(result).isInstanceOf(ExtractionResult.Extracted.class);
    ChallengeSpec spec = ((ExtractionResult.Extracted) result).spec();
    System.out.println(((ExtractionResult.Extracted) result).specJson());
    assertThat(spec.aiPolicy().level()).isEqualTo(ChallengeSpec.AiPolicyLevel.FORBIDDEN);
    assertThat(spec.tasks()).isNotEmpty();
  }
}
