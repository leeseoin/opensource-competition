CREATE TABLE collection_jobs (
    job_id VARCHAR(128) PRIMARY KEY,
    merchant VARCHAR(64) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    search_query VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_tasks INTEGER NOT NULL,
    start_page INTEGER NOT NULL,
    end_page INTEGER NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_collection_jobs_status CHECK (
        status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'PARTIAL', 'FAILED')
    ),
    CONSTRAINT ck_collection_jobs_task_count CHECK (total_tasks >= 1),
    CONSTRAINT ck_collection_jobs_page_range CHECK (
        start_page >= 1 AND end_page >= start_page
    )
);

CREATE INDEX idx_collection_jobs_requested_at
    ON collection_jobs(requested_at DESC);

CREATE TABLE collection_tasks (
    task_id VARCHAR(128) PRIMARY KEY,
    job_id VARCHAR(128) NOT NULL REFERENCES collection_jobs(job_id) ON DELETE CASCADE,
    page INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    product_count INTEGER NOT NULL DEFAULT 0,
    verification_total INTEGER NOT NULL DEFAULT 0,
    verification_matched INTEGER NOT NULL DEFAULT 0,
    verification_mismatched INTEGER NOT NULL DEFAULT 0,
    verification_failed INTEGER NOT NULL DEFAULT 0,
    verification_missing_in_html INTEGER NOT NULL DEFAULT 0,
    verification_missing_in_json INTEGER NOT NULL DEFAULT 0,
    verification_pending INTEGER NOT NULL DEFAULT 0,
    requested_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    duration_ms BIGINT,
    error_code VARCHAR(100),
    error_message VARCHAR(1000),
    error_retryable BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_collection_tasks_job_page UNIQUE (job_id, page),
    CONSTRAINT ck_collection_tasks_status CHECK (
        status IN ('QUEUED', 'SUCCESS', 'PARTIAL', 'FAILED', 'PUBLISH_FAILED')
    ),
    CONSTRAINT ck_collection_tasks_page CHECK (page >= 1),
    CONSTRAINT ck_collection_tasks_product_count CHECK (product_count >= 0),
    CONSTRAINT ck_collection_tasks_duration CHECK (duration_ms IS NULL OR duration_ms >= 0)
);

CREATE INDEX idx_collection_tasks_job_id
    ON collection_tasks(job_id, page);

CREATE INDEX idx_collection_tasks_status
    ON collection_tasks(status);
