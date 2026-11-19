-- Stop componente3-usuarios and take a verified database backup before running.
-- Existing numeric catalog IDs are preserved as decimal strings.
ALTER TABLE purchase_history
    MODIFY COLUMN movie_id VARCHAR(255) NOT NULL;

ALTER TABLE users
    MODIFY COLUMN cinema_id VARCHAR(255) NULL;

ALTER TABLE role_requests
    MODIFY COLUMN cinema_id VARCHAR(255) NULL;
