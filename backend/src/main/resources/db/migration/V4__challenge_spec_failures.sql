-- A failed extraction is stored too (errors, no spec), so the same statement is not sent to Claude
-- again on every sync. Exactly one of spec_json and errors is set.
ALTER TABLE challenge_spec ALTER COLUMN spec_json DROP NOT NULL;
ALTER TABLE challenge_spec ADD COLUMN errors text[];
ALTER TABLE challenge_spec ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE challenge_spec
    ADD CONSTRAINT challenge_spec_spec_or_errors CHECK ((spec_json IS NULL) <> (errors IS NULL));
