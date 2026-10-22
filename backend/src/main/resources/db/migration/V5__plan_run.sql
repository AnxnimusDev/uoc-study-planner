-- Course start: "Setmana N" in a title means the N-th 7-day block from it (recommended date).
ALTER TABLE course ADD COLUMN start_at timestamptz;

-- Planner task ids ("<challenge>:<spec task>", "quiz:<id>", "review:...") are stable per challenge.
ALTER TABLE task ADD CONSTRAINT task_challenge_spec_task_uq UNIQUE (challenge_id, spec_task_id);

-- Things the student must know but the planner cannot schedule (recording windows, margin gone).
-- Stored because the calendar feeds are served by another process than the one that plans.
CREATE TABLE plan_notice (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_version bigint      NOT NULL REFERENCES plan_version (id),
    task_id      text        NOT NULL,
    type         text        NOT NULL CHECK (type IN ('VIDEO_WINDOW', 'MARGIN_SKIPPED')),
    starts_at    timestamptz NOT NULL,
    ends_at      timestamptz,
    message      text        NOT NULL
);

CREATE INDEX plan_notice_version_idx ON plan_notice (plan_version);
