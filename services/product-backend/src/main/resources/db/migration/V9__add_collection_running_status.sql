ALTER TABLE collection_jobs
    DROP CONSTRAINT ck_collection_jobs_status;

ALTER TABLE collection_jobs
    ADD CONSTRAINT ck_collection_jobs_status CHECK (
        status IN ('QUEUED', 'RUNNING', 'PROCESSING', 'COMPLETED', 'PARTIAL', 'FAILED')
    );

ALTER TABLE collection_tasks
    DROP CONSTRAINT ck_collection_tasks_status;

ALTER TABLE collection_tasks
    ADD CONSTRAINT ck_collection_tasks_status CHECK (
        status IN ('QUEUED', 'RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED', 'PUBLISH_FAILED')
    );
