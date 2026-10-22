package edu.uoc.planner.canvas;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the Canvas mirror, plus the challenge status that later steps advance. */
@Service
public class CanvasCatalog {

  private final JdbcClient jdbc;

  CanvasCatalog(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Challenges of the selected courses, by course and module position. */
  @Transactional(readOnly = true)
  public List<StoredChallenge> selectedChallenges() {
    Map<Long, List<Deliverable>> deliverables = new HashMap<>();
    jdbc.sql(
            """
            SELECT d.* FROM deliverable d
            JOIN challenge c ON c.id = d.challenge_id
            JOIN course co ON co.canvas_id = c.course_id
            WHERE co.selected
            ORDER BY d.due_at NULLS LAST, d.canvas_assignment_id
            """)
        .query(
            (rs, row) -> {
              deliverables
                  .computeIfAbsent(rs.getLong("challenge_id"), id -> new ArrayList<>())
                  .add(
                      new Deliverable(
                          rs.getLong("canvas_assignment_id"),
                          rs.getString("name"),
                          instant(rs.getTimestamp("unlock_at")),
                          instant(rs.getTimestamp("due_at")),
                          instant(rs.getTimestamp("lock_at")),
                          rs.getBigDecimal("points") == null
                              ? java.math.BigDecimal.ZERO
                              : rs.getBigDecimal("points"),
                          rs.getBoolean("counts_for_grade"),
                          strings(rs.getArray("submission_types"))));
              return null;
            })
        .list();
    return jdbc.sql(
            """
            SELECT c.id, c.course_id, co.code, co.start_at, c.position, c.name, c.weight_pct, c.status,
                   (SELECT body_html FROM canvas_page p
                     WHERE p.challenge_id = c.id AND p.role = 'STATEMENT' LIMIT 1) AS statement,
                   (SELECT body_html FROM canvas_page p
                     WHERE p.challenge_id = c.id AND p.role = 'APPROACH' LIMIT 1) AS approach,
                   (SELECT body_html FROM canvas_page p
                     WHERE p.challenge_id = c.id AND p.role = 'STUDY_GUIDE' LIMIT 1) AS guide
            FROM challenge c JOIN course co ON co.canvas_id = c.course_id
            WHERE co.selected
            ORDER BY co.canvas_id, c.position
            """)
        .query(
            (rs, row) ->
                new StoredChallenge(
                    rs.getLong("id"),
                    rs.getLong("course_id"),
                    rs.getString("code"),
                    instant(rs.getTimestamp("start_at")),
                    rs.getInt("position"),
                    rs.getString("name"),
                    rs.getBigDecimal("weight_pct"),
                    ChallengeStatus.valueOf(rs.getString("status")),
                    rs.getString("statement"),
                    rs.getString("approach"),
                    rs.getString("guide"),
                    deliverables.getOrDefault(rs.getLong("id"), List.of())))
        .list();
  }

  /** A finished challenge ({@code DONE}) keeps its status. */
  @Transactional
  public void updateStatus(long challengeId, ChallengeStatus status) {
    jdbc.sql("UPDATE challenge SET status = :status WHERE id = :id AND status <> 'DONE'")
        .param("status", status.name())
        .param("id", challengeId)
        .update();
  }

  private static Instant instant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

  private static List<String> strings(Array array) throws SQLException {
    return array == null ? List.of() : List.of((String[]) array.getArray());
  }
}
