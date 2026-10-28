-- Nuevos campos en transaction
ALTER TABLE transaction
    ADD COLUMN IF NOT EXISTS asset_name VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS exchange   VARCHAR(20)  NULL,
    ADD COLUMN IF NOT EXISTS broker     VARCHAR(50)  NULL,
    ADD COLUMN IF NOT EXISTS currency   VARCHAR(3)   NULL;

COMMENT ON COLUMN transaction.asset_name IS 'Nombre completo del activo (ej: NVIDIA Corporation)';
COMMENT ON COLUMN transaction.exchange   IS 'Bolsa de origen (ej: NASDAQGS, BMV, NYSE)';
COMMENT ON COLUMN transaction.broker     IS 'Broker/plataforma (ej: GBM, IBKR, Binance)';
COMMENT ON COLUMN transaction.currency   IS 'Moneda ISO-4217 de la transaccion (USD, MXN, EUR)';

-- Tabla dividend_detail
CREATE TABLE IF NOT EXISTS dividend_detail (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id   UUID          NOT NULL
                                   REFERENCES transaction(transaction_id)
                                   ON DELETE CASCADE,
    ex_dividend_date DATE          NULL,
    dividend_type    VARCHAR(10)   NOT NULL DEFAULT 'CASH'
                                   CHECK (dividend_type IN ('CASH','STOCK')),
    tax_withheld     NUMERIC(18,8) NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dividend_detail_transaction_id
    ON dividend_detail(transaction_id);

COMMENT ON TABLE  dividend_detail                  IS 'Detalle de transacciones DIVIDEND';
COMMENT ON COLUMN dividend_detail.ex_dividend_date IS 'Fecha ex-dividendo';
COMMENT ON COLUMN dividend_detail.dividend_type    IS 'CASH=efectivo, STOCK=reinversion DRIP';
COMMENT ON COLUMN dividend_detail.tax_withheld     IS 'Retencion fiscal (ej: 30% retencion EE.UU.)';
