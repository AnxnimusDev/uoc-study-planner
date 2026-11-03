package edu.uoc.planner.scheduling.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@code task_progress}: what the student reported done or half done (ADR 0011). */
@Service
public class ProgressStore {

  /**
   * @param challengeId null for time with no challenge
   * @param specVersion version of the spec the task came from; null when it did not come from one
   */
  public record Entry(
      String taskId,
      Long challengeId,
      Integer specVersion,
      int remainingMinutes,
      Instant reportedAt) {}

  private final JdbcClient jdbc;

  ProgressStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Replaces what was reported before for the same task. */
  @Transactional
  public void save(Entry entry) {
    jdbc.sql(
            """
            INSERT INTO task_progress
              (task_id, challenge_id, spec_version, remaining_minutes, reported_at)
            VALUES (:task, :challenge, :version, :minutes, :at)
            ON CONFLICT (task_id) DO UPDATE SET
              challenge_id = EXCLUDED.challenge_id,
              spec_version = EXCLUDED.spec_version,
              remaining_minutes = EXCLUDED.remaining_minutes,
              reported_at = EXCLUDED.reported_at
            """)
        .param("task", entry.taskId())
        .param("challenge", entry.challengeId())
        .param("version", entry.specVersion())
        .param("minutes", entry.remainingMinutes())
        .param("at", Timestamp.from(entry.reportedAt()))
        .update();
  }

  @Transactional(readOnly = true)
  public List<Entry> all() {
    return jdbc.sql(
            """
            SELECT task_id, challenge_id, spec_version, remaining_minutes, reported_at
            FROM task_progress ORDER BY task_id
            """)
        .query(
            (rs, row) ->
                new Entry(
                    rs.getString("task_id"),
                    rs.getObject("challenge_id", Long.class),
                    rs.getObject("spec_version", Integer.class),
                    rs.getInt("remaining_minutes"),
                    rs.getTimestamp("reported_at").toInstant()))
        .list();
  }

  @Transactional
  public void delete(String taskId) {
    jdbc.sql("DELETE FROM task_progress WHERE task_id = :task").param("task", taskId).update();
  }
}
