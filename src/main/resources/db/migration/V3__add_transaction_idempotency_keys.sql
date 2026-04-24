CREATE TABLE IF NOT EXISTS transaction_idempotency_keys (
    record_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    operation_scope VARCHAR(160) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_transaction_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_transaction_idempotency_user_scope_key
        UNIQUE (user_id, operation_scope, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_transaction_idempotency_expires_at
    ON transaction_idempotency_keys (expires_at);
