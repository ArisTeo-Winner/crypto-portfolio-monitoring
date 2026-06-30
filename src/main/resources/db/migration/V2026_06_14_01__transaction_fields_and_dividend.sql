ALTER TABLE transaction
    ADD COLUMN IF NOT EXISTS asset_name        VARCHAR(255)  NULL,
    ADD COLUMN IF NOT EXISTS exchange          VARCHAR(20)   NULL,
    ADD COLUMN IF NOT EXISTS broker            VARCHAR(50)   NULL,
    ADD COLUMN IF NOT EXISTS currency          VARCHAR(3)    NULL,
    ADD COLUMN IF NOT EXISTS face_value        NUMERIC(18,8) NULL,
    ADD COLUMN IF NOT EXISTS maturity_date     DATE          NULL,
    ADD COLUMN IF NOT EXISTS coupon_rate       NUMERIC(8,4)  NULL,
    ADD COLUMN IF NOT EXISTS auto_reinvestment BOOLEAN       NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN transaction.asset_name        IS 'Nombre completo del activo';
COMMENT ON COLUMN transaction.exchange          IS 'Bolsa origen (NASDAQGS, NYSE, BMV, cetesdirecto)';
COMMENT ON COLUMN transaction.broker            IS 'Broker (GBM, IBKR, Bursanet, cetesdirecto)';
COMMENT ON COLUMN transaction.currency          IS 'Moneda ISO-4217 (USD, MXN)';
COMMENT ON COLUMN transaction.face_value        IS 'Valor nominal al vencimiento (bonos)';
COMMENT ON COLUMN transaction.maturity_date     IS 'Fecha de vencimiento (bonos)';
COMMENT ON COLUMN transaction.coupon_rate       IS 'Tasa anual % (11.38 CETES91, 0 cupon cero)';
COMMENT ON COLUMN transaction.auto_reinvestment IS 'Reinversion automatica al vencimiento';

CREATE TABLE IF NOT EXISTS dividend_detail (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id   UUID          NOT NULL
                                   REFERENCES transaction(transaction_id) ON DELETE CASCADE,
    ex_dividend_date DATE          NULL,
    dividend_type    VARCHAR(10)   NOT NULL DEFAULT 'CASH'
                                   CHECK (dividend_type IN ('CASH','STOCK')),
    tax_withheld     NUMERIC(18,8) NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_dividend_detail_transaction_id
    ON dividend_detail(transaction_id);
