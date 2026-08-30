ALTER TABLE transaction
    ADD COLUMN IF NOT EXISTS broker_commission NUMERIC(18,8) NULL,
    ADD COLUMN IF NOT EXISTS broker_iva        NUMERIC(18,8) NULL,
    ADD COLUMN IF NOT EXISTS other_fees        NUMERIC(18,8) NULL,
    ADD COLUMN IF NOT EXISTS review_status     VARCHAR(20)   NULL
                                               CHECK (review_status IN ('OK','REQUIERE_REVISION'));

COMMENT ON COLUMN transaction.broker_commission IS 'Comision de corretaje (desglose de fee); NULL si no hay ticket';
COMMENT ON COLUMN transaction.broker_iva        IS 'IVA sobre la comision (solo MXN casa de bolsa); NULL si no aplica';
COMMENT ON COLUMN transaction.other_fees        IS 'Fees adicionales: transaction/regulatory/clearing (DriveWealth)';
COMMENT ON COLUMN transaction.review_status     IS 'REQUIERE_REVISION si |neto calculado - reportado| > 0.05; NULL si no evaluado';
