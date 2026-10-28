-- ADR-0014 F6b: detalle del subtipo MERGER a nivel seguridad (lo que define el deal, igual para
-- todos los tenedores). 1:1 con corporate_action (CTI, PK compartida). El evento vive sobre el
-- PREDECESOR (asset_symbol = ticker absorbido); pivotal_date = fecha efectiva de la fusion.
--   merger_type      = ALL_CASH | ALL_STOCK | CASH_AND_STOCK.
--   successor_symbol = ticker que recibe el tenedor (la sucesora). NULL en ALL_CASH.
--   exchange_ratio   = acciones de la sucesora por accion del predecesor. NULL en ALL_CASH.
--   cash_per_share   = efectivo por accion del predecesor. NULL en ALL_STOCK.
-- El accrual por usuario (carryover basis / PnL realizado) y el muteo del predecesor son F6b-2.
CREATE TABLE merger_action_detail (
    corporate_action_id UUID          NOT NULL,
    merger_type         VARCHAR(20)   NOT NULL,
    successor_symbol    VARCHAR(20),
    exchange_ratio      NUMERIC(18,8),
    cash_per_share      NUMERIC(18,8),
    currency            VARCHAR(3),
    CONSTRAINT pk_merger_action_detail PRIMARY KEY (corporate_action_id),
    CONSTRAINT fk_merger_action_detail_ca FOREIGN KEY (corporate_action_id)
        REFERENCES corporate_action (id) ON DELETE CASCADE,
    CONSTRAINT ck_merger_type CHECK (merger_type IN ('ALL_CASH', 'ALL_STOCK', 'CASH_AND_STOCK'))
);
