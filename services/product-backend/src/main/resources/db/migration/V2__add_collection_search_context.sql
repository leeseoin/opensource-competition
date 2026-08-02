CREATE TABLE collection_search_contexts (
    request_id VARCHAR(128) PRIMARY KEY,
    merchant VARCHAR(64) NOT NULL,
    search_query VARCHAR(200) NOT NULL,
    filters JSONB NOT NULL DEFAULT '{}'::jsonb,
    collected_at TIMESTAMPTZ NOT NULL,
    collector_version VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_collection_search_contexts_merchant
    ON collection_search_contexts(merchant);

CREATE INDEX idx_offer_snapshots_request_id
    ON offer_snapshots(request_id);
