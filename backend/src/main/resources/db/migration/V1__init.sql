-- Initial schema. See docs/design.md, section 10.
-- Instants are timestamptz. date/time columns hold the student's local calendar (Europe/Madrid).
-- jsonb only for challenge spec, rubric and check-in answers.

CREATE TABLE course (
    canvas_id    bigint PRIMARY KEY,
    code         text     NOT NULL,
    name         text     NOT NULL,
    term_id      bigint   NOT NULL,
    ics_url      text,
    selected     boolean  NOT NULL DEFAULT false,
    margin_days  smallint CHECK (margin_days >= 0)
);

CREATE TABLE challenge (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    course_id         bigint       NOT NULL REFERENCES course (canvas_id),
    canvas_module_id  bigint       NOT NULL UNIQUE,
    position          integer      NOT NULL,
    name              text         NOT NULL,
    weight_pct        numeric(5, 2) CHECK (weight_pct BETWEEN 0 AND 100),
    status            text         NOT NULL
        CHECK (status IN ('SYNCED', 'EXTRACTED', 'NEEDS_REVIEW', 'AT_RISK', 'DONE'))
);

CREATE INDEX challenge_course_idx ON challenge (course_id);

CREATE TABLE canvas_page (
    course_id  bigint      NOT NULL REFERENCES course (canvas_id),
    page_url   text        NOT NULL,
    title      text        NOT NULL,
    role       text        NOT NULL,
    body_html  text,
    body_hash  text,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (course_id, page_url)
);

CREATE TABLE deliverable (
    canvas_assignment_id bigint PRIMARY KEY,
    challenge_id         bigint        NOT NULL REFERENCES challenge (id),
    name                 text          NOT NULL,
    unlock_at            timestamptz,
    due_at               timestamptz,
    lock_at              timestamptz,
    points               numeric(8, 2),
    counts_for_grade     boolean       NOT NULL,
    submission_types     text[]        NOT NULL DEFAULT '{}',
    rubric_json          jsonb
);

CREATE INDEX deliverable_challenge_idx ON deliverable (challenge_id);

CREATE TABLE challenge_spec (
    challenge_id bigint  NOT NULL REFERENCES challenge (id),
    version      integer NOT NULL CHECK (version > 0),
    spec_json    jsonb   NOT NULL,
    model        text    NOT NULL,
    source_hash  text    NOT NULL,
    user_edited  boolean NOT NULL DEFAULT false,
    PRIMARY KEY (challenge_id, version)
);

CREATE TABLE task (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    challenge_id    bigint        NOT NULL REFERENCES challenge (id),
    spec_task_id    text,
    title           text          NOT NULL,
    kind            text          NOT NULL
        CHECK (kind IN ('WORK', 'READING', 'QUIZ', 'FORUM_POST', 'FORUM_REPLY',
                        'VIDEO_RECORDING', 'REVIEW', 'SUBMISSION')),
    estimated_hours numeric(5, 2) NOT NULL CHECK (estimated_hours > 0),
    soft_deadline   timestamptz,
    hard_deadline   timestamptz,
    -- ids of tasks this one depends on (task.id)
    depends_on      bigint[]      NOT NULL DEFAULT '{}'
);

CREATE INDEX task_challenge_idx ON task (challenge_id);

CREATE TABLE plan_version (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at        timestamptz   NOT NULL,
    trigger           text          NOT NULL CHECK (trigger IN ('SYNC', 'CHECKIN', 'MANUAL')),
    unscheduled_hours numeric(6, 2) NOT NULL DEFAULT 0 CHECK (unscheduled_hours >= 0)
);

CREATE TABLE study_session (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id      bigint      NOT NULL REFERENCES task (id),
    starts_at    timestamptz NOT NULL,
    ends_at      timestamptz NOT NULL,
    plan_version bigint      NOT NULL REFERENCES plan_version (id),
    state        text        NOT NULL CHECK (state IN ('PLANNED', 'DONE', 'SKIPPED')),
    CHECK (ends_at > starts_at)
);

CREATE INDEX study_session_task_idx ON study_session (task_id);
CREATE INDEX study_session_plan_idx ON study_session (plan_version, starts_at);

CREATE TABLE availability_block (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    day_of_week smallint NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- ISO-8601, 1 = Monday
    start_time  time     NOT NULL,
    end_time    time     NOT NULL,
    type        text     NOT NULL,
    label       text,
    CHECK (end_time > start_time)
);

CREATE TABLE availability_override (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    date       date NOT NULL,
    -- both null: the whole day
    start_time time,
    end_time   time,
    effect     text NOT NULL CHECK (effect IN ('BUSY', 'FREE', 'OFFICE_DAY')),
    CHECK ((start_time IS NULL) = (end_time IS NULL)),
    CHECK (end_time > start_time)
);

CREATE INDEX availability_override_date_idx ON availability_override (date);

CREATE TABLE checkin (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind         text        NOT NULL CHECK (kind IN ('WEEKLY', 'MONTHLY')),
    period_start date        NOT NULL,
    answers_json jsonb,
    answered_at  timestamptz,
    UNIQUE (kind, period_start)
);

CREATE TABLE external_ref (
    entity_type text   NOT NULL,
    entity_id   bigint NOT NULL,
    system      text   NOT NULL,
    external_id text   NOT NULL,
    PRIMARY KEY (entity_type, entity_id, system)
);

CREATE TABLE user_settings (
    key   text PRIMARY KEY,
    value bytea NOT NULL -- encrypted with PLANNER_SECRET_KEY
);

CREATE TABLE session_secret (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cookie_blob   bytea       NOT NULL, -- encrypted with PLANNER_SECRET_KEY
    captured_at   timestamptz NOT NULL,
    last_valid_at timestamptz
);
