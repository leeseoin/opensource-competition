CREATE TABLE agent_runs (
    run_id UUID PRIMARY KEY,
    research_session_id UUID NOT NULL REFERENCES research_sessions(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    verification_id UUID REFERENCES offer_verification_requests(verification_id) ON DELETE SET NULL,
    error_code VARCHAR(64),
    error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_agent_runs_research_session UNIQUE (research_session_id)
);

CREATE INDEX idx_agent_runs_status_updated_at
    ON agent_runs (status, updated_at DESC);

CREATE TABLE agent_run_events (
    event_id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES agent_runs(run_id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_run_events_sequence UNIQUE (run_id, sequence_no)
);

CREATE INDEX idx_agent_run_events_run_created
    ON agent_run_events (run_id, sequence_no);

CREATE TABLE agent_run_collection_jobs (
    run_id UUID NOT NULL REFERENCES agent_runs(run_id) ON DELETE CASCADE,
    job_id VARCHAR(128) NOT NULL REFERENCES collection_jobs(job_id) ON DELETE CASCADE,
    merchant VARCHAR(64) NOT NULL,
    data_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (run_id, job_id)
);

CREATE INDEX idx_agent_run_collection_jobs_job_id
    ON agent_run_collection_jobs (job_id);
