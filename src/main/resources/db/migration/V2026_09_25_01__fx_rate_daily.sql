-- Cache inmutable del tipo de cambio FIX diario (Banxico SIE, serie SF43718) por fecha de
-- determinacion, para valuar operaciones al FX de su fecha de transaccion (ADR-0009, trade-date FX).
CREATE TABLE IF NOT EXISTS fx_rate_daily (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker     VARCHAR(10)   NOT NULL,
    rate_date  DATE          NOT NULL,
    rate       NUMERIC(18,8) NOT NULL,
    provider   VARCHAR(20)   NOT NULL DEFAULT 'BANXICO',
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (ticker, rate_date)
);
CREATE INDEX IF NOT EXISTS idx_fx_daily_ticker_date
    ON fx_rate_daily(ticker, rate_date DESC);
