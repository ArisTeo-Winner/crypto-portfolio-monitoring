-- ADR-0014 F3a: detalle del subtipo DIVIDEND a nivel SEGURIDAD (lo que declara la empresa, igual
-- para todos). 1:1 con corporate_action (Class Table Inheritance, PK compartida). La cadena fiscal
-- (retenciones US+MX) y el accrual por usuario NO viven aqui: eso es la transaccion DIVIDEND del
-- usuario (F3b/F4). Campos alineados al payload de Massive (/stocks/v1/dividends).
CREATE TABLE dividend_action_detail (
    corporate_action_id     UUID          NOT NULL,
    amount_per_share        NUMERIC(18,8) NOT NULL,   -- DPS bruto (cash_amount)
    currency                VARCHAR(3)    NOT NULL,
    frequency               SMALLINT,                 -- 4=trimestral,12=mensual,2=semestral,1=anual,0=unica
    distribution_type       VARCHAR(20)   NOT NULL,   -- RECURRING | SPECIAL
    split_adjusted_dps      NUMERIC(18,8),            -- DPS normalizado por splits (comparable entre anios)
    price_adjustment_factor NUMERIC(12,8),            -- factor de ajuste de precio del proveedor
    CONSTRAINT pk_dividend_action_detail PRIMARY KEY (corporate_action_id),
    CONSTRAINT fk_dividend_action_detail_ca FOREIGN KEY (corporate_action_id)
        REFERENCES corporate_action (id) ON DELETE CASCADE,
    CONSTRAINT ck_dividend_distribution_type CHECK (distribution_type IN ('RECURRING','SPECIAL'))
);
