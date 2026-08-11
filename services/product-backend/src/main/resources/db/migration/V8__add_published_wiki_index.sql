CREATE TABLE wiki_pages (
    id BIGSERIAL PRIMARY KEY,
    page_id VARCHAR(100) NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PUBLISHED', 'SUPERSEDED')),
    title VARCHAR(200) NOT NULL,
    reviewed_by VARCHAR(200) NOT NULL,
    reviewed_at TIMESTAMPTZ NOT NULL,
    supersedes VARCHAR(200),
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_wiki_pages_page_version UNIQUE (page_id, version)
);

CREATE TABLE wiki_claims (
    id BIGSERIAL PRIMARY KEY,
    wiki_page_id BIGINT NOT NULL REFERENCES wiki_pages(id) ON DELETE CASCADE,
    claim_id VARCHAR(100) NOT NULL,
    subject VARCHAR(100) NOT NULL,
    relation VARCHAR(40) NOT NULL CHECK (relation IN ('narrower', 'synonym', 'merchant_category')),
    object VARCHAR(100) NOT NULL,
    derived BOOLEAN NOT NULL,
    confidence NUMERIC(4, 3) NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    source_ids JSONB NOT NULL,
    evidence_pointers JSONB NOT NULL,
    CONSTRAINT uk_wiki_claims_page_claim UNIQUE (wiki_page_id, claim_id)
);

CREATE INDEX idx_wiki_claims_subject_relation
    ON wiki_claims (LOWER(subject), relation);

CREATE INDEX idx_wiki_claims_synonym_object
    ON wiki_claims (LOWER(object))
    WHERE relation = 'synonym';
