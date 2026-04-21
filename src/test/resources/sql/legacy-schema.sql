CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID,
    refresh_token VARCHAR(255),
    created_at TIMESTAMP,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    ip_address VARCHAR(255),
    user_agent VARCHAR(255),
    revoked_at TIMESTAMPTZ
);

CREATE TABLE sessions (
    session_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    refresh_token_id UUID,
    login_time TIMESTAMPTZ,
    logout_time TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE audit_logs (
    log_id UUID PRIMARY KEY,
    user_id UUID,
    event_type VARCHAR(50) NOT NULL,
    description VARCHAR(2048) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(2048),
    event_timestamp TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_auth_providers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    auth_provider VARCHAR(32) NOT NULL,
    provider_id VARCHAR(191),
    provider_email VARCHAR(191),
    created_at TIMESTAMP NOT NULL,
    last_login TIMESTAMP
);

CREATE TABLE portfolio_entry (
    portfolio_entry_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    asset_symbol VARCHAR(10) NOT NULL,
    asset_type VARCHAR(20) NOT NULL,
    total_quantity NUMERIC(18, 8) NOT NULL DEFAULT 0,
    total_invested NUMERIC(18, 2) NOT NULL DEFAULT 0,
    average_price_per_unit NUMERIC(18, 8) NOT NULL DEFAULT 0,
    last_transaction_price NUMERIC(18, 8),
    current_value NUMERIC(18, 2),
    total_profit_loss NUMERIC(18, 2),
    last_updated TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT
);

CREATE TABLE transaction (
    transaction_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    portfolio_entry_id UUID NOT NULL,
    asset_symbol VARCHAR(10) NOT NULL,
    asset_type VARCHAR(20) NOT NULL,
    transaction_type VARCHAR(10) NOT NULL,
    transfer_type VARCHAR(20),
    quantity NUMERIC(18, 8) NOT NULL,
    price_per_unit NUMERIC(18, 8) NOT NULL,
    total_value NUMERIC(18, 2) NOT NULL,
    transaction_date TIMESTAMP,
    fee NUMERIC(18, 2),
    notes TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
