CREATE TABLE IF NOT EXISTS bmv_price_history (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    emisora_serie VARCHAR(20)   NOT NULL,
    trade_date    DATE          NOT NULL,
    close_price   NUMERIC(18,4) NOT NULL,
    amount_traded NUMERIC(20,2) NULL,
    provider      VARCHAR(20)   NOT NULL DEFAULT 'DATABURSATIL',
    cached_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (emisora_serie, trade_date)
);
CREATE INDEX IF NOT EXISTS idx_bmv_history_symbol_date
    ON bmv_price_history(emisora_serie, trade_date DESC);

CREATE TABLE IF NOT EXISTS market_price_snapshot (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_symbol    VARCHAR(20)   NOT NULL,
    exchange        VARCHAR(20)   NOT NULL,
    currency        VARCHAR(3)    NOT NULL,
    price_close     NUMERIC(18,4) NOT NULL,
    price_open      NUMERIC(18,4) NULL,
    price_high      NUMERIC(18,4) NULL,
    price_low       NUMERIC(18,4) NULL,
    price_avg       NUMERIC(18,4) NULL,
    price_change    NUMERIC(18,4) NULL,
    pct_change      NUMERIC(8,4)  NULL,
    volume          BIGINT        NULL,
    importe_operado NUMERIC(20,2) NULL,
    provider        VARCHAR(20)   NOT NULL,
    quote_timestamp TIMESTAMPTZ   NOT NULL,
    cached_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_snapshot_symbol
    ON market_price_snapshot(asset_symbol, exchange, currency);

CREATE TABLE IF NOT EXISTS market_fx_snapshot (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker      VARCHAR(10)   NOT NULL,
    rate        NUMERIC(10,4) NOT NULL,
    pct_change  NUMERIC(8,4)  NULL,
    abs_change  NUMERIC(10,4) NULL,
    provider    VARCHAR(20)   NOT NULL DEFAULT 'DATABURSATIL',
    quote_at    TIMESTAMPTZ   NOT NULL,
    cached_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_fx_ticker_date
    ON market_fx_snapshot(ticker, quote_at DESC);
