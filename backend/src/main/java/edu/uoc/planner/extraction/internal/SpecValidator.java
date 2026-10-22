package edu.uoc.planner.extraction.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Full JSON Schema check plus the rules the schema cannot express (docs/design.md, section 6). The
 * rules run even when the schema fails, so the retry gets every problem at once. Messages are in
 * Spanish: they go back to the model and to the student.
 */
public final class SpecValidator {

  /** Above this a challenge plan is almost certainly over-split or invented. */
  static final double MAX_TOTAL_HOURS = 80;

  private static final int MIN_DAYS_AFTER_OWN_POST = 2;

  private final ObjectMapper mapper = new ObjectMapper();
  private final Schema schema;

  public SpecValidator() {
    this.schema =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(SpecSchema.full());
  }

  /**
   * @param deliverableRefs references the spec may use ({@code assignment:<id>})
   * @return problems found; empty when valid
   */
  public List<String> validate(String json, Set<String> deliverableRefs) {
    JsonNode spec;
    try {
      spec = mapper.readTree(json);
    } catch (JsonProcessingException e) {
      return List.of("La respuesta no es JSON válido: " + e.getOriginalMessage());
    }
    if (spec == null || !spec.isObject()) {
      return List.of("La respuesta no es un objeto JSON");
    }
    List<String> errors = new ArrayList<>();
    schema.validate(spec).forEach(e -> errors.add(e.getInstanceLocation() + ": " + e.getMessage()));
    checkDeliverables(spec, deliverableRefs, errors);
    checkTasks(spec, errors);
    return errors;
  }

  private static void checkDeliverables(JsonNode spec, Set<String> known, List<String> errors) {
    for (JsonNode deliverable : spec.path("deliverables")) {
      String ref = deliverable.path("ref").asText();
      if (!known.contains(ref)) {
        errors.add("La entrega " + ref + " no está en la lista de entregas del módulo");
      }
    }
    for (JsonNode task : spec.path("tasks")) {
      JsonNode ref = task.get("deliverableRef");
      if (ref != null && !known.contains(ref.asText())) {
        errors.add(
            "La tarea "
                + task.path("id").asText()
                + " apunta a "
                + ref.asText()
                + ", que no está en la lista de entregas del módulo");
      }
    }
  }

  private static void checkTasks(JsonNode spec, List<String> errors) {
    Map<String, List<String>> dependencies = new LinkedHashMap<>();
    double total = 0;
    for (JsonNode task : spec.path("tasks")) {
      String id = task.path("id").asText();
      if (dependencies.containsKey(id)) {
        errors.add("Id de tarea duplicado: " + id);
      }
      List<String> dependsOn = new ArrayList<>();
      task.path("dependsOn").forEach(d -> dependsOn.add(d.asText()));
      dependencies.put(id, dependsOn);
      total += task.path("estimatedHours").asDouble(0);
      if ("FORUM_REPLY".equals(task.path("kind").asText())
          && (dependsOn.isEmpty()
              || task.path("minDaysAfterDependency").asInt(0) < MIN_DAYS_AFTER_OWN_POST)) {
        errors.add(
            "La tarea "
                + id
                + " (FORUM_REPLY) debe depender de la publicación propia con minDaysAfterDependency >= "
                + MIN_DAYS_AFTER_OWN_POST);
      }
    }
    dependencies.forEach(
        (id, dependsOn) ->
            dependsOn.stream()
                .filter(d -> !dependencies.containsKey(d))
                .forEach(
                    d -> errors.add("La tarea " + id + " depende de " + d + ", que no existe")));
    findCycle(dependencies)
        .ifPresent(
            cycle -> errors.add("Las dependencias forman un ciclo: " + String.join(" → ", cycle)));
    if (total > MAX_TOTAL_HOURS) {
      errors.add(
          String.format(
              "El total de horas (%.1f) supera el máximo razonable de %.0f h por reto",
              total, MAX_TOTAL_HOURS));
    }
  }

  private static java.util.Optional<List<String>> findCycle(Map<String, List<String>> graph) {
    Map<String, Integer> state = new HashMap<>(); // 1 visiting, 2 done
    for (String start : graph.keySet()) {
      List<String> path = new ArrayList<>();
      if (visit(start, graph, state, path, new HashSet<>())) {
        return java.util.Optional.of(path);
      }
    }
    return java.util.Optional.empty();
  }

  private static boolean visit(
      String id,
      Map<String, List<String>> graph,
      Map<String, Integer> state,
      List<String> path,
      Set<String> onPath) {
    Integer current = state.get(id);
    if (current != null && current == 2) {
      return false;
    }
    path.add(id);
    if (!onPath.add(id)) {
      return true;
    }
    state.put(id, 1);
    for (String next : graph.getOrDefault(id, List.of())) {
      if (graph.containsKey(next) && visit(next, graph, state, path, onPath)) {
        return true;
      }
    }
    onPath.remove(id);
    path.removeLast();
    state.put(id, 2);
    return false;
  }
}
