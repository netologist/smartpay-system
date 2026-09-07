-- ==============================================================================
-- Flyway Migration: V1__init_transactional_outbox.sql
-- Domain: Transactional Outbox Pattern for Guaranteed At-Least-Once Kafka Delivery
-- Database: PostgreSQL 16
-- ==============================================================================

CREATE TABLE transactional_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NULL
);

-- Partial index strictly on unprocessed events: guarantees O(1) polling speed
-- when executing: SELECT * FROM transactional_outbox WHERE processed_at IS NULL ORDER BY created_at FOR UPDATE SKIP LOCKED
CREATE INDEX idx_outbox_unprocessed ON transactional_outbox(created_at ASC)
WHERE processed_at IS NULL;

CREATE INDEX idx_outbox_aggregate ON transactional_outbox(aggregate_type, aggregate_id);
