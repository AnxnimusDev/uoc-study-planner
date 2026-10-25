-- Weekly study time (ADR 0007): a task belongs to a challenge or, without one, to a course.
ALTER TABLE task ALTER COLUMN challenge_id DROP NOT NULL;
ALTER TABLE task ADD COLUMN course_id bigint REFERENCES course (canvas_id);
ALTER TABLE task
    ADD CONSTRAINT task_challenge_or_course CHECK ((challenge_id IS NULL) <> (course_id IS NULL));
-- The (challenge_id, spec_task_id) key does not cover rows without a challenge.
ALTER TABLE task ADD CONSTRAINT task_course_spec_task_uq UNIQUE (course_id, spec_task_id);
