ALTER TABLE collection_tasks
    ADD COLUMN idempotency_key VARCHAR(80) NOT NULL DEFAULT '';

ALTER TABLE collection_tasks
    ALTER COLUMN idempotency_key DROP DEFAULT;

CREATE INDEX idx_collection_tasks_idempotency_key_status
    ON collection_tasks(idempotency_key, status);
