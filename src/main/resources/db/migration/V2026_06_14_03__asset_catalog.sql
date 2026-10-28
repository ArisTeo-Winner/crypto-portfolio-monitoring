CREATE TABLE IF NOT EXISTS asset_catalog (
    symbol     VARCHAR(20)  PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    asset_type VARCHAR(20)  NOT NULL,
    logo_url   VARCHAR(500) NULL,
    exchange   VARCHAR(20)  NULL,
    currency   VARCHAR(3)   NULL,
    market_cap BIGINT       NULL,
    is_popular BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_asset_catalog_type ON asset_catalog(asset_type);
