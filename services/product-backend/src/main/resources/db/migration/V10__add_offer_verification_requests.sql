CREATE TABLE offer_verification_requests (
    verification_id UUID PRIMARY KEY,
    merchant_product_id BIGINT NOT NULL REFERENCES merchant_products(id) ON DELETE CASCADE,
    job_id VARCHAR(128) NOT NULL REFERENCES collection_jobs(job_id) ON DELETE CASCADE,
    baseline_price_amount BIGINT,
    baseline_currency VARCHAR(3),
    baseline_stock_status VARCHAR(32) NOT NULL,
    baseline_collected_at TIMESTAMPTZ NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_offer_verification_baseline_price CHECK (
        baseline_price_amount IS NULL OR baseline_price_amount >= 0
    )
);

CREATE INDEX idx_offer_verification_requests_job_id
    ON offer_verification_requests(job_id);

CREATE INDEX idx_offer_verification_requests_product_requested
    ON offer_verification_requests(merchant_product_id, requested_at DESC);
