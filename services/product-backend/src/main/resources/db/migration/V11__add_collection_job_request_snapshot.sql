ALTER TABLE collection_jobs
    ADD COLUMN request_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE collection_jobs
    ALTER COLUMN request_snapshot DROP DEFAULT;
