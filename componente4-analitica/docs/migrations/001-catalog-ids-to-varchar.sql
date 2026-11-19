-- Stop componente4-analitica and take a verified database backup before running.
-- Existing numeric catalog IDs are preserved as decimal strings.
BEGIN;

ALTER TABLE analytics_events
    ALTER COLUMN cinema_id TYPE VARCHAR(255) USING cinema_id::text,
    ALTER COLUMN room_id TYPE VARCHAR(255) USING room_id::text,
    ALTER COLUMN session_id TYPE VARCHAR(255) USING session_id::text,
    ALTER COLUMN movie_id TYPE VARCHAR(255) USING movie_id::text;

COMMIT;
