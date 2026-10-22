package edu.uoc.planner.extraction.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.uoc.planner.Fixtures;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class SpecValidatorTest {

  private static final Set<String> REFS = Set.of("assignment:979687", "assignment:979674");
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final SpecValidator validator = new SpecValidator();

  @Test
  void acceptsAValidSpec() {
    assertThat(validator.validate(Fixtures.json("claude/algebra-r1-spec.json"), REFS)).isEmpty();
  }

  @Test
  void rejectsPropertiesOutsideTheSchema() {
    assertThat(validate(s -> s.put("solution", "x = 1 ± i"))).anyMatch(e -> e.contains("solution"));
  }

  @Test
  void rejectsMissingRequiredFields() {
    assertThat(validate(s -> s.remove("aiPolicy"))).anyMatch(e -> e.contains("aiPolicy"));
  }

  @Test
  void rejectsTasksLongerThanOneSession() {
    assertThat(validate(s -> task(s, 1).put("estimatedHours", 5))).isNotEmpty();
  }

  @Test
  void rejectsReferencesToUnknownDeliverables() {
    assertThat(validate(s -> task(s, 2).put("deliverableRef", "assignment:123456")))
        .anyMatch(e -> e.contains("assignment:123456"));
    assertThat(
            validate(
                s -> ((ObjectNode) s.withArray("deliverables").get(0)).put("ref", "assignment:1")))
        .anyMatch(e -> e.contains("assignment:1"));
  }

  @Test
  void rejectsUnknownDependencies() {
    assertThat(validate(s -> task(s, 1).putArray("dependsOn").add("t9")))
        .anyMatch(e -> e.contains("t9"));
  }

  @Test
  void rejectsDependencyCycles() {
    assertThat(validate(s -> task(s, 0).putArray("dependsOn").add("t4")))
        .anyMatch(e -> e.contains("ciclo"));
  }

  @Test
  void rejectsDuplicatedTaskIds() {
    assertThat(validate(s -> task(s, 1).put("id", "t1"))).anyMatch(e -> e.contains("t1"));
  }

  @Test
  void requiresForumRepliesToWaitForTheOwnPost() {
    assertThat(
            validate(
                s -> {
                  ObjectNode reply = task(s, 3).put("kind", "FORUM_REPLY");
                  reply.remove("deliverableRef");
                  reply.put("minDaysAfterDependency", 1);
                }))
        .anyMatch(e -> e.contains("FORUM_REPLY"));
  }

  @Test
  void rejectsAnUnreasonableTotal() {
    assertThat(
            validate(
                s -> {
                  ArrayNode tasks = s.withArray("tasks");
                  for (int i = 0; i < 30; i++) {
                    tasks
                        .addObject()
                        .put("id", "x" + i)
                        .put("title", "Part " + i)
                        .put("estimatedHours", 3)
                        .put("kind", "WORK")
                        .putArray("dependsOn");
                  }
                }))
        .anyMatch(e -> e.contains("total"));
  }

  @Test
  void rejectsSomethingThatIsNotJson() {
    assertThat(validator.validate("Aquí tens el JSON: {", REFS)).hasSize(1);
  }

  private java.util.List<String> validate(Consumer<ObjectNode> change) {
    try {
      ObjectNode spec = (ObjectNode) MAPPER.readTree(Fixtures.json("claude/algebra-r1-spec.json"));
      change.accept(spec);
      return validator.validate(MAPPER.writeValueAsString(spec), REFS);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static ObjectNode task(ObjectNode spec, int index) {
    return (ObjectNode) spec.withArray("tasks").get(index);
  }
}
