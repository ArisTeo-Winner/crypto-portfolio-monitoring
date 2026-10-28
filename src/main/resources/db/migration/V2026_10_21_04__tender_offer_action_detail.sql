-- ADR-0014 F6c: detalle del subtipo TENDER_OFFER (OPA) a nivel seguridad. 1:1 con corporate_action
-- (CTI, PK compartida). El evento vive sobre la seguridad objeto de la oferta; pivotal_date = fecha
-- de la oferta/liquidacion. DELISTING NO lleva tabla propia: se representa con una fila base
-- corporate_action(DELISTING) + asset_catalog.market_status='DELISTED' (F6a).
--   offer_price           = precio por accion ofrecido.
--   offeror_symbol        = ticker del oferente (si cotiza), opcional.
--   resulted_in_delisting = true si la OPA llevo a baja de cotizacion (caso TRAXION).
-- El efecto por usuario (cash-out electivo / muteo) es F6c-2.
CREATE TABLE tender_offer_action_detail (
    corporate_action_id   UUID          NOT NULL,
    offer_price           NUMERIC(18,8) NOT NULL,
    currency              VARCHAR(3),
    offeror_symbol        VARCHAR(20),
    resulted_in_delisting BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_tender_offer_action_detail PRIMARY KEY (corporate_action_id),
    CONSTRAINT fk_tender_offer_action_detail_ca FOREIGN KEY (corporate_action_id)
        REFERENCES corporate_action (id) ON DELETE CASCADE
);
