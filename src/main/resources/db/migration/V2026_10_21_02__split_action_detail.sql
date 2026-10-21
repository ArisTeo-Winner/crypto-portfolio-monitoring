-- ADR-0014 F2b: reconduce el SPLIT (ADR-0011) a la base CTI. Crea el detalle 1:1 del subtipo SPLIT
-- y BACKFILLea el historico existente de stock_split hacia corporate_action + split_action_detail.
-- stock_split sigue siendo la fuente de la VALUACION (ADR-0011) — esto NO la toca; solo suma el
-- evento a la espina comun para el timeline/eventos unificados.
CREATE TABLE split_action_detail (
    corporate_action_id UUID           NOT NULL,
    share_multiplier    NUMERIC(18,10) NOT NULL,   -- split_to/split_from: cantidad *= , precio /=
    split_from          NUMERIC(18,6),
    split_to            NUMERIC(18,6),
    adjustment_type     VARCHAR(20),               -- forward_split | reverse_split | stock_dividend
    CONSTRAINT pk_split_action_detail PRIMARY KEY (corporate_action_id),
    CONSTRAINT fk_split_action_detail_ca FOREIGN KEY (corporate_action_id)
        REFERENCES corporate_action (id) ON DELETE CASCADE
);

-- Backfill idempotente: una fila base por cada split historico aun no reconducido. provider_event_id
-- = 'split:'||id para trazabilidad y para unir ambos INSERT. La guarda por llave natural respeta el
-- UNIQUE(asset_symbol, action_type, pivotal_date) de la base.
WITH reconducted AS (
    INSERT INTO corporate_action
        (id, asset_symbol, action_type, status, pivotal_date, source_provider,
         provider_event_id, synced_at)
    SELECT gen_random_uuid(), ss.ticker, 'SPLIT', 'COMPLETED', ss.execution_date, ss.provider,
           'split:' || ss.id, now()
    FROM stock_split ss
    WHERE NOT EXISTS (
        SELECT 1 FROM corporate_action ca
        WHERE ca.asset_symbol = ss.ticker
          AND ca.action_type = 'SPLIT'
          AND ca.pivotal_date = ss.execution_date)
    RETURNING id, provider_event_id
)
INSERT INTO split_action_detail
    (corporate_action_id, share_multiplier, split_from, split_to, adjustment_type)
SELECT r.id, ss.share_multiplier, ss.split_from, ss.split_to, ss.adjustment_type
FROM reconducted r
JOIN stock_split ss ON ('split:' || ss.id) = r.provider_event_id;
