-- Stop componente2-reservas and take a verified database backup before running.
-- Existing numeric catalog IDs are preserved as decimal strings.
BEGIN;

ALTER TABLE reservations
    ALTER COLUMN show_id TYPE VARCHAR(255) USING show_id::text,
    ALTER COLUMN movie_id TYPE VARCHAR(255) USING movie_id::text,
    ALTER COLUMN room_id TYPE VARCHAR(255) USING room_id::text;

ALTER TABLE reserved_seats
    ALTER COLUMN show_id TYPE VARCHAR(255) USING show_id::text;

COMMIT;
