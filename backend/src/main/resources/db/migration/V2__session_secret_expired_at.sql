-- A 401 from Canvas marks the stored session as expired (ADR 0005). Kept, not deleted, to measure
-- how long a UOC session lasts (docs/canvas-api-notes.md, "Pendiente de verificar").
ALTER TABLE session_secret ADD COLUMN expired_at timestamptz;
