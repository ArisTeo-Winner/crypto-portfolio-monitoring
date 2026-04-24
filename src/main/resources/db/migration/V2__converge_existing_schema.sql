DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'users'
    ) AND EXISTS (
        SELECT LOWER(email)
        FROM users
        WHERE email IS NOT NULL
        GROUP BY LOWER(email)
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Flyway V2 aborted: duplicate users.email values exist when compared case-insensitively. Clean the duplicates before applying uq_users_email_lower.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower ON users (LOWER(email));

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_active_user ON sessions (user_id, is_active);

CREATE INDEX IF NOT EXISTS idx_audit_logs_user_timestamp ON audit_logs (user_id, event_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_event_type_timestamp ON audit_logs (event_type, event_timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_user_auth_providers_lookup
    ON user_auth_providers (auth_provider, provider_id);

CREATE INDEX IF NOT EXISTS idx_portfolio_entry_asset_symbol ON portfolio_entry (asset_symbol);
CREATE INDEX IF NOT EXISTS idx_portfolio_entry_user_asset_type ON portfolio_entry (user_id, asset_type);

CREATE INDEX IF NOT EXISTS idx_transaction_user_date
    ON transaction (user_id, transaction_date DESC, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transaction_user_asset_symbol_date
    ON transaction (user_id, asset_symbol, transaction_date DESC, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transaction_user_asset_type_date
    ON transaction (user_id, asset_type, transaction_date DESC, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transaction_user_type_date
    ON transaction (user_id, transaction_type, transaction_date DESC, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transaction_portfolio_entry_id ON transaction (portfolio_entry_id);
