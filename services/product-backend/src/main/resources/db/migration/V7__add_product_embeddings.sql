CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE product_embeddings (
    merchant_product_id BIGINT NOT NULL REFERENCES merchant_products(id) ON DELETE CASCADE,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(200) NOT NULL,
    model_version VARCHAR(200) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    embedding VECTOR(1024) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (merchant_product_id, provider, model, model_version)
);

CREATE INDEX idx_product_embeddings_cosine
    ON product_embeddings USING HNSW (embedding vector_cosine_ops);

CREATE INDEX idx_product_embeddings_content_hash
    ON product_embeddings (content_hash);
