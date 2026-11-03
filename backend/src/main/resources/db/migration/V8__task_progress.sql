-- ADR 0011: time left that the student reported for a task (zero: done), by plan task id.
-- spec_version is set for tasks of an extracted spec: a newer extraction makes it stale.
CREATE TABLE task_progress (
  task_id           text PRIMARY KEY,
  challenge_id      bigint REFERENCES challenge(id),
  spec_version      integer,
  remaining_minutes integer NOT NULL CHECK (remaining_minutes >= 0),
  reported_at       timestamptz NOT NULL
);
