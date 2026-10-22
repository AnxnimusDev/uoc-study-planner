package edu.uoc.planner.extraction;

import java.sql.Array;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Versions of {@code challenge_spec}: every extraction, valid or not, adds one. */
@Service
public class SpecStore {

  private final JdbcClient jdbc;

  SpecStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public Optional<StoredSpec> latest(long challengeId) {
    return jdbc.sql(
            """
            SELECT version, source_hash, model, spec_json::text AS spec, errors, user_edited
            FROM challenge_spec WHERE challenge_id = :id ORDER BY version DESC LIMIT 1
            """)
        .param("id", challengeId)
        .query(
            (rs, row) -> {
              Array errors = rs.getArray("errors");
              return new StoredSpec(
                  rs.getInt("version"),
                  rs.getString("source_hash"),
                  rs.getString("model"),
                  rs.getString("spec"),
                  errors == null ? List.of() : List.of((String[]) errors.getArray()),
                  rs.getBoolean("user_edited"));
            })
        .optional();
  }

  @Transactional
  public void save(long challengeId, ExtractionResult result) {
    int version =
        jdbc.sql(
                "SELECT coalesce(max(version), 0) + 1 FROM challenge_spec WHERE challenge_id = :id")
            .param("id", challengeId)
            .query(Integer.class)
            .single();
    String spec = result instanceof ExtractionResult.Extracted e ? e.specJson() : null;
    String[] errors =
        result instanceof ExtractionResult.NeedsReview r ? r.errors().toArray(String[]::new) : null;
    jdbc.sql(
            """
            INSERT INTO challenge_spec (challenge_id, version, spec_json, errors, model, source_hash)
            VALUES (:id, :version, CAST(:spec AS jsonb), :errors, :model, :hash)
            """)
        .param("id", challengeId)
        .param("version", version)
        .param("spec", spec)
        .param("errors", errors)
        .param("model", result.model())
        .param("hash", result.sourceHash())
        .update();
  }
}
