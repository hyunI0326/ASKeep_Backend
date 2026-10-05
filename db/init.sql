CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS material_chunks (
    id BIGSERIAL PRIMARY KEY,
    material_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    page_number INTEGER,
    embedding VECTOR(384),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);