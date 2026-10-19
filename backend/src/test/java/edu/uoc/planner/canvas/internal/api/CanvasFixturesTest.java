package edu.uoc.planner.canvas.internal.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.uoc.planner.Fixtures;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CanvasFixturesTest {

  private static final Map<String, Class<?>> TYPES =
      Map.of(
          "users-self.json", CanvasUser.class,
          "courses.json", CanvasCourse.class,
          "algebra-tabs.json", CanvasTab.class,
          "algebra-modules.json", CanvasModule.class,
          "ipo-modules.json", CanvasModule.class,
          "algebra-page-enunciat-r1.json", CanvasPage.class,
          "ipo-page-enunciat-r1.json", CanvasPage.class,
          "algebra-assignments.json", CanvasAssignment.class,
          "algebra-assignment-groups.json", CanvasAssignmentGroup.class);

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());
  private static final PropertyNamingStrategies.NamingBase SNAKE_CASE =
      (PropertyNamingStrategies.NamingBase) PropertyNamingStrategies.SNAKE_CASE;
  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  static Stream<Arguments> fixtures() {
    return TYPES.entrySet().stream().map(e -> Arguments.of(e.getKey(), e.getValue()));
  }

  @Test
  void everyCanvasFixtureHasARecord() throws IOException, URISyntaxException {
    Path dir = Path.of(CanvasFixturesTest.class.getResource("/fixtures/canvas").toURI());
    try (Stream<Path> files = Files.list(dir)) {
      assertThat(files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json")))
          .containsExactlyInAnyOrderElementsOf(TYPES.keySet());
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void deserializesWithoutMissingRequiredFields(String fixture, Class<?> type) throws IOException {
    for (Object element : deserialize(fixture, type)) {
      assertThat(VALIDATOR.validate(element))
          .as("%s in %s", type.getSimpleName(), fixture)
          .isEmpty();
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void keepsEveryMappedValuePresentInTheJson(String fixture, Class<?> type) throws IOException {
    JsonNode tree = MAPPER.readTree(Fixtures.json("canvas/" + fixture));
    List<Object> records = deserialize(fixture, type);
    List<JsonNode> nodes = tree.isArray() ? toList(tree) : List.of(tree);

    List<String> lost = new ArrayList<>();
    for (int i = 0; i < records.size(); i++) {
      collectLostValues(nodes.get(i), records.get(i), fixture + "[" + i + "]", lost);
    }
    assertThat(lost).as("JSON values that did not reach the record").isEmpty();
  }

  @Test
  void mapsOnlyPropertiesThatCanvasReturns() throws IOException {
    Map<Class<?>, Set<String>> seen = new HashMap<>();
    for (Map.Entry<String, Class<?>> e : TYPES.entrySet()) {
      JsonNode tree = MAPPER.readTree(Fixtures.json("canvas/" + e.getKey()));
      for (JsonNode node : tree.isArray() ? toList(tree) : List.of(tree)) {
        collectProperties(node, e.getValue(), seen);
      }
    }

    List<String> unknown = new ArrayList<>();
    seen.forEach(
        (type, properties) -> {
          for (RecordComponent component : type.getRecordComponents()) {
            String property = SNAKE_CASE.translate(component.getName());
            if (!properties.contains(property)) {
              unknown.add(type.getSimpleName() + "." + component.getName() + " -> " + property);
            }
          }
        });
    assertThat(unknown).as("record components with no matching JSON property").isEmpty();
  }

  private static List<Object> deserialize(String fixture, Class<?> type) throws IOException {
    JsonNode tree = MAPPER.readTree(Fixtures.json("canvas/" + fixture));
    List<Object> records = new ArrayList<>();
    for (JsonNode node : tree.isArray() ? toList(tree) : List.of(tree)) {
      records.add(MAPPER.treeToValue(node, type));
    }
    return records;
  }

  private static void collectLostValues(
      JsonNode json, Object record, String path, List<String> lost) {
    for (RecordComponent component : record.getClass().getRecordComponents()) {
      String property = SNAKE_CASE.translate(component.getName());
      JsonNode value = json.get(property);
      if (value == null || value.isNull()) {
        continue;
      }
      Object mapped = read(component, record);
      if (mapped == null) {
        lost.add(path + "." + property);
      } else if (mapped instanceof List<?> list && value.isArray()) {
        for (int i = 0; i < list.size(); i++) {
          if (list.get(i) instanceof Record nested) {
            collectLostValues(value.get(i), nested, path + "." + property + "[" + i + "]", lost);
          }
        }
      }
    }
  }

  private static void collectProperties(
      JsonNode json, Class<?> type, Map<Class<?>, Set<String>> seen) {
    Set<String> properties = seen.computeIfAbsent(type, t -> new HashSet<>());
    json.fieldNames().forEachRemaining(properties::add);
    for (RecordComponent component : type.getRecordComponents()) {
      JsonNode value = json.get(SNAKE_CASE.translate(component.getName()));
      if (value != null
          && value.isArray()
          && component.getGenericType() instanceof ParameterizedType list
          && list.getActualTypeArguments()[0] instanceof Class<?> element
          && element.isRecord()) {
        value.forEach(item -> collectProperties(item, element, seen));
      }
    }
  }

  private static Object read(RecordComponent component, Object record) {
    try {
      return component.getAccessor().invoke(record);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static List<JsonNode> toList(JsonNode array) {
    List<JsonNode> nodes = new ArrayList<>();
    array.forEach(nodes::add);
    return nodes;
  }
}
