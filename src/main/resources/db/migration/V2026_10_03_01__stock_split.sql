-- Eventos de split de acciones (fuente estructurada Massive/Alpha Vantage, ADR-0011).
-- Inmutable una vez publicado; el ajuste de titulos se aplica en la proyeccion por comparacion de
-- fechas (tx.transaction_date < execution_date). shareMultiplier = split_to/split_from.
CREATE TABLE IF NOT EXISTS stock_split (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker          VARCHAR(20)   NOT NULL,
    execution_date  DATE          NOT NULL,
    split_from      NUMERIC(18,6) NULL,
    split_to        NUMERIC(18,6) NULL,
    share_multiplier NUMERIC(18,10) NOT NULL,
    adjustment_type VARCHAR(20)   NULL,
    provider        VARCHAR(20)   NOT NULL DEFAULT 'MASSIVE',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (ticker, execution_date)
);
CREATE INDEX IF NOT EXISTS idx_stock_split_ticker ON stock_split(ticker, execution_date DESC);

-- Marcador de ultima sincronizacion por ticker (distingue "sin splits" de "nunca consultado"
-- y permite un TTL de refetch sin golpear el API en el hot path).
CREATE TABLE IF NOT EXISTS stock_split_sync (
    ticker     VARCHAR(20)  PRIMARY KEY,
    synced_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
