CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    phone_number VARCHAR(255),
    address VARCHAR(255),
    city VARCHAR(255),
    state VARCHAR(255),
    postal_code VARCHAR(255),
    country VARCHAR(255),
    date_of_birth DATE,
    profile_picture BYTEA,
    bio VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    last_login TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_username ON users (username);

CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_roles_name ON roles (name);

CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(255) NOT NULL,
    description VARCHAR(255)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_permissions_code ON permissions (code);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles (role_id);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
);

CREATE INDEX IF NOT EXISTS idx_role_permissions_permission_id ON role_permissions (permission_id);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID,
    refresh_token VARCHAR(255),
    created_at TIMESTAMP,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    ip_address VARCHAR(255),
    user_agent VARCHAR(255),
    revoked_at TIMESTAMPTZ,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_refresh_tokens_refresh_token ON refresh_tokens (refresh_token);

CREATE TABLE IF NOT EXISTS sessions (
    session_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    refresh_token_id UUID,
    login_time TIMESTAMPTZ,
    logout_time TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sessions_refresh_token_id ON sessions (refresh_token_id);

CREATE TABLE IF NOT EXISTS audit_logs (
    log_id UUID PRIMARY KEY,
    user_id UUID,
    event_type VARCHAR(50) NOT NULL,
    description VARCHAR(2048) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(2048),
    event_timestamp TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS user_auth_providers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    auth_provider VARCHAR(32) NOT NULL,
    provider_id VARCHAR(191),
    provider_email VARCHAR(191),
    created_at TIMESTAMP NOT NULL,
    last_login TIMESTAMP,
    CONSTRAINT uq_user_provider UNIQUE (user_id, auth_provider),
    CONSTRAINT fk_user_auth_providers_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS portfolio_entry (
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
    version BIGINT,
    CONSTRAINT uq_portfolio_entry_user_symbol UNIQUE (user_id, asset_symbol)
);

CREATE TABLE IF NOT EXISTS transaction (
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
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_transaction_user FOREIGN KEY (user_id) REFERENCES users (id)
);
