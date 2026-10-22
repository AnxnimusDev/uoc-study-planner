package edu.uoc.planner.planning;

import edu.uoc.planner.extraction.ChallengeSpec.TaskKind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The plan in {@code plan_version}, {@code task}, {@code study_session} and {@code plan_notice}.
 * Every plan adds a version; only the latest plan's sessions and notices are kept.
 */
@Service
public class PlanStore {

  private static final String SESSION_COLUMNS =
      """
      SELECT s.starts_at, s.ends_at, t.spec_task_id, t.title, t.kind, t.challenge_id,
             split_part(co.code, ' - ', 1) AS course
      FROM study_session s
      JOIN task t ON t.id = s.task_id
      JOIN challenge c ON c.id = t.challenge_id
      JOIN course co ON co.canvas_id = c.course_id
      """;

  private final JdbcClient jdbc;

  PlanStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * @param tasks the planner input; tasks the planner added (reviews) are taken from the sessions
   * @return the new {@code plan_version.id}
   */
  @Transactional
  public long save(Plan plan, List<PlanTask> tasks, PlanTrigger trigger, Instant createdAt) {
    long version =
        jdbc.sql(
                """
                INSERT INTO plan_version (created_at, trigger, unscheduled_hours)
                VALUES (:created, :trigger, :hours) RETURNING id
                """)
            .param("created", Timestamp.from(createdAt))
            .param("trigger", trigger.name())
            .param("hours", hours(plan.unscheduled()))
            .query(Long.class)
            .single();

    Map<String, Long> ids = new HashMap<>();
    for (PlanTask task : tasks) {
      ids.put(
          task.id(),
          upsertTask(
              task.challengeId(),
              task.id(),
              task.title(),
              task.kind(),
              task.estimate(),
              task.recommendedBy(),
              task.dueAt()));
    }
    Map<String, PlannedSession> extra = new LinkedHashMap<>();
    Map<String, Duration> extraTime = new HashMap<>();
    for (PlannedSession s : plan.sessions()) {
      if (!ids.containsKey(s.taskId())) {
        extra.putIfAbsent(s.taskId(), s);
        extraTime.merge(s.taskId(), s.duration(), Duration::plus);
      }
    }
    extra.forEach(
        (id, s) ->
            ids.put(
                id,
                upsertTask(
                    s.challengeId(), id, s.title(), s.kind(), extraTime.get(id), null, null)));
    for (PlanTask task : tasks) {
      Long[] dependsOn =
          task.dependsOn().stream().map(ids::get).filter(d -> d != null).toArray(Long[]::new);
      jdbc.sql("UPDATE task SET depends_on = :deps WHERE id = :id")
          .param("deps", dependsOn)
          .param("id", ids.get(task.id()))
          .update();
    }

    jdbc.sql("DELETE FROM study_session").update();
    jdbc.sql("DELETE FROM plan_notice").update();
    for (PlannedSession s : plan.sessions()) {
      jdbc.sql(
              """
              INSERT INTO study_session (task_id, starts_at, ends_at, plan_version, state)
              VALUES (:task, :start, :end, :version, 'PLANNED')
              """)
          .param("task", ids.get(s.taskId()))
          .param("start", Timestamp.from(s.start()))
          .param("end", Timestamp.from(s.end()))
          .param("version", version)
          .update();
    }
    for (Notice n : plan.notices()) {
      jdbc.sql(
              """
              INSERT INTO plan_notice (plan_version, task_id, type, starts_at, ends_at, message)
              VALUES (:version, :task, :type, :start, :end, :message)
              """)
          .param("version", version)
          .param("task", n.taskId())
          .param("type", n.type().name())
          .param("start", Timestamp.from(n.from()))
          .param("end", n.to() == null ? null : Timestamp.from(n.to()))
          .param("message", n.message())
          .update();
    }
    return version;
  }

  @Transactional(readOnly = true)
  public List<PlannedSession> latestSessions() {
    return jdbc.sql(
            SESSION_COLUMNS
                + " WHERE s.plan_version = (SELECT max(id) FROM plan_version)"
                + " ORDER BY s.starts_at, t.spec_task_id")
        .query((rs, row) -> session(rs, false))
        .list();
  }

  /** Sessions of the latest plan starting in {@code [now, now + window)}: kept when replanning. */
  @Transactional(readOnly = true)
  public List<PlannedSession> sessionsToKeep(Instant now, Duration window) {
    return jdbc.sql(
            SESSION_COLUMNS
                + " WHERE s.plan_version = (SELECT max(id) FROM plan_version)"
                + " AND s.starts_at >= :from AND s.starts_at < :to"
                + " ORDER BY s.starts_at, t.spec_task_id")
        .param("from", Timestamp.from(now))
        .param("to", Timestamp.from(now.plus(window)))
        .query((rs, row) -> session(rs, true))
        .list();
  }

  @Transactional(readOnly = true)
  public List<Notice> latestNotices() {
    return jdbc.sql(
            """
            SELECT task_id, type, starts_at, ends_at, message FROM plan_notice
            WHERE plan_version = (SELECT max(id) FROM plan_version) ORDER BY starts_at, task_id
            """)
        .query(
            (rs, row) ->
                new Notice(
                    rs.getString("task_id"),
                    Notice.Type.valueOf(rs.getString("type")),
                    rs.getTimestamp("starts_at").toInstant(),
                    rs.getTimestamp("ends_at") == null
                        ? null
                        : rs.getTimestamp("ends_at").toInstant(),
                    rs.getString("message")))
        .list();
  }

  private long upsertTask(
      long challengeId,
      String taskId,
      String title,
      TaskKind kind,
      Duration estimate,
      Instant softDeadline,
      Instant hardDeadline) {
    return jdbc.sql(
            """
            INSERT INTO task (challenge_id, spec_task_id, title, kind, estimated_hours,
                              soft_deadline, hard_deadline)
            VALUES (:challenge, :id, :title, :kind, :hours, :soft, :hard)
            ON CONFLICT (challenge_id, spec_task_id) DO UPDATE
              SET title = EXCLUDED.title, kind = EXCLUDED.kind,
                  estimated_hours = EXCLUDED.estimated_hours,
                  soft_deadline = EXCLUDED.soft_deadline, hard_deadline = EXCLUDED.hard_deadline
            RETURNING id
            """)
        .param("challenge", challengeId)
        .param("id", taskId)
        .param("title", title)
        .param("kind", kind.name())
        .param("hours", hours(estimate).max(new BigDecimal("0.01")))
        .param("soft", softDeadline == null ? null : Timestamp.from(softDeadline))
        .param("hard", hardDeadline == null ? null : Timestamp.from(hardDeadline))
        .query(Long.class)
        .single();
  }

  private static PlannedSession session(ResultSet rs, boolean kept) throws SQLException {
    return new PlannedSession(
        rs.getString("spec_task_id"),
        rs.getLong("challenge_id"),
        rs.getString("course"),
        rs.getString("title"),
        TaskKind.valueOf(rs.getString("kind")),
        rs.getTimestamp("starts_at").toInstant(),
        rs.getTimestamp("ends_at").toInstant(),
        kept);
  }

  private static BigDecimal hours(Duration duration) {
    return BigDecimal.valueOf(duration.toMinutes())
        .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
  }
}
