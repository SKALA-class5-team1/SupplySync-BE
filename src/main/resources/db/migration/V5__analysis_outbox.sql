CREATE TABLE analysis_outbox (
    message_id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    type VARCHAR(40) NOT NULL CHECK (type IN ('analysis.requested', 'analysis.resume')),
    body JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error TEXT
);

CREATE INDEX idx_analysis_outbox_pending ON analysis_outbox(created_at)
    WHERE published_at IS NULL;
