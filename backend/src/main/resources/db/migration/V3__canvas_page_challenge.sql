-- Pages belong to a challenge module: the extractor reads a challenge's statement, study guide and
-- approach together. Nullable because a page may appear outside challenge modules.
ALTER TABLE canvas_page ADD COLUMN challenge_id bigint REFERENCES challenge (id);
CREATE INDEX canvas_page_challenge_idx ON canvas_page (challenge_id);
