CREATE EXTENSION IF NOT EXISTS pg_trgm;

UPDATE research_sessions
SET conditions = (conditions - 'productType' - 'usage' - 'price' - 'colors' - 'sizes' - 'requirements')
    || JSONB_BUILD_OBJECT(
        'productType', JSONB_BUILD_OBJECT(
            'value', conditions ->> 'productType',
            'priority', 'required'
        ),
        'usage', COALESCE((
            SELECT JSONB_AGG(JSONB_BUILD_OBJECT('value', item.value, 'priority', 'preferred'))
            FROM JSONB_ARRAY_ELEMENTS_TEXT(conditions -> 'usage') AS item(value)
        ), '[]'::JSONB),
        'price', (conditions -> 'price') || '{"priority":"required"}'::JSONB,
        'colors', COALESCE((
            SELECT JSONB_AGG(JSONB_BUILD_OBJECT('value', item.value, 'priority', 'preferred'))
            FROM JSONB_ARRAY_ELEMENTS_TEXT(conditions -> 'colors') AS item(value)
        ), '[]'::JSONB),
        'sizes', COALESCE((
            SELECT JSONB_AGG(JSONB_BUILD_OBJECT('value', item.value, 'priority', 'required'))
            FROM JSONB_ARRAY_ELEMENTS_TEXT(conditions -> 'sizes') AS item(value)
        ), '[]'::JSONB),
        'requirements', COALESCE((
            SELECT JSONB_AGG(JSONB_BUILD_OBJECT('value', item.value, 'priority', 'required'))
            FROM JSONB_ARRAY_ELEMENTS_TEXT(conditions -> 'requirements') AS item(value)
        ), '[]'::JSONB)
    )
WHERE JSONB_TYPEOF(conditions -> 'productType') = 'string';

ALTER TABLE products
    ADD COLUMN search_text TEXT
    GENERATED ALWAYS AS (
        LOWER(COALESCE(name, '') || ' ' || COALESCE(brand, ''))
    ) STORED;

CREATE INDEX idx_products_search_text_fts
    ON products USING GIN (TO_TSVECTOR('simple', search_text));

CREATE INDEX idx_products_search_text_trgm
    ON products USING GIN (search_text gin_trgm_ops);

CREATE INDEX idx_collection_search_contexts_query_trgm
    ON collection_search_contexts USING GIN (LOWER(search_query) gin_trgm_ops);
