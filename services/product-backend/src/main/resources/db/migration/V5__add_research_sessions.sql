CREATE TABLE research_sessions (
    id UUID PRIMARY KEY,
    question VARCHAR(1000) NOT NULL,
    runtime VARCHAR(32) NOT NULL,
    plugin_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    conditions JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMPTZ
);

CREATE INDEX idx_research_sessions_status_created_at
    ON research_sessions (status, created_at DESC);
