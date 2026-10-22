package edu.uoc.planner.extraction.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.uoc.planner.Fixtures;
import org.junit.jupiter.api.Test;

class HtmlCleanerTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void dropsInlineStylesAndLayoutMarkup() {
    String raw = body("ipo-page-enunciat-r1.json");

    String clean = HtmlCleaner.clean(raw);

    assertThat(clean).doesNotContain("style=", "class=", "<span", "<div", "<img");
    assertThat(clean.length()).isLessThan(raw.length() / 2);
  }

  @Test
  void keepsStructureAndLinks() {
    String clean = HtmlCleaner.clean(body("ipo-page-enunciat-r1.json"));

    assertThat(clean).containsPattern("<h[1-6]>").contains("<li>");
    assertThat(clean).contains("href=\"https://aula.uoc.edu/courses/87921/assignments/921977\"");
  }

  @Test
  void keepsTheAiPolicySentenceAndUnresolvedReferences() {
    String clean = HtmlCleaner.clean(body("algebra-page-enunciat-r1.json"));

    assertThat(clean)
        .contains("En aquesta activitat no està permès l'ús d'eines d'intel·ligència artificial")
        .contains("$CANVAS_OBJECT_REFERENCE$/discussion_topics/");
  }

  @Test
  void removesScriptsAndTheirContent() {
    String clean =
        HtmlCleaner.clean("<p>Enunciat</p><script>alert('x')</script><style>p{color:red}</style>");

    assertThat(clean).isEqualTo("<p>Enunciat</p>");
  }

  @Test
  void returnsEmptyForMissingHtml() {
    assertThat(HtmlCleaner.clean(null)).isEmpty();
    assertThat(HtmlCleaner.clean("  ")).isEmpty();
  }

  private static String body(String fixture) {
    try {
      return MAPPER.readTree(Fixtures.json("canvas/" + fixture)).get("body").asText();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
