-- ==============================================================================
-- Flyway Migration: V1__init_notification_logs.sql (STORY-008: Table V9)
-- Domain: Event-Driven Multi-Channel Notification Engine Logs & Audit Trail
-- Database: PostgreSQL 16
-- Schema: notification
-- ==============================================================================

CREATE TABLE notification_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    template_code VARCHAR(64) NULL,
    rendered_content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_message_id VARCHAR(128) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    dispatched_at TIMESTAMP WITH TIME ZONE NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_notification_channel CHECK (channel IN ('SMS', 'EMAIL', 'WEBHOOK')),
    CONSTRAINT chk_notification_status CHECK (status IN ('PENDING', 'DISPATCHED', 'FAILED', 'DEAD_LETTERED')),
    CONSTRAINT uq_notification_event_channel UNIQUE (event_id, channel)
);

CREATE INDEX idx_notification_logs_event_id ON notification_logs(event_id);
CREATE INDEX idx_notification_logs_status ON notification_logs(status);
CREATE INDEX idx_notification_logs_created_at ON notification_logs(created_at);
