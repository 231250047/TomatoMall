-- Dedicated PostgreSQL database only. Default DashScope text-embedding-v4 dimension: 1024.
-- For another model/dimension, create a NEW database and adjust vector(N) before initialization.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS vector_store (
    id uuid PRIMARY KEY,
    content text NOT NULL,
    metadata jsonb NOT NULL,
    embedding vector(1024) NOT NULL
);
CREATE TABLE IF NOT EXISTS rag_index_config (
    id integer PRIMARY KEY CHECK(id=1),
    fingerprint text NOT NULL
);
-- Exact search is intentional for the initial small catalog. No HNSW performance claim.
