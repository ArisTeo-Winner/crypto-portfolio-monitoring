-- ADR-0014 F2a: tabla BASE del framework de corporate actions (Class Table Inheritance).
-- Espina comun de TODO evento (split, dividendo, fusion, OPA, delisting, quiebra, spin-off);
-- el detalle especifico por tipo vive en tablas hijas (split_detail, dividend_detail, ...) o en
-- raw_payload (cola larga). Aditiva: NO toca stock_split (ADR-0011), que se reconduce en F2b.
-- Fechas = DATE (evento de calendario, sin zona — distinto a la EJECUCION de ADR-0013).
CREATE TABLE corporate_action (
    id                UUID         NOT NULL,
    asset_symbol      VARCHAR(20)  NOT NULL,   -- liga logica al security master (asset_catalog.symbol)
    action_type       VARCHAR(20)  NOT NULL,
    status            VARCHAR(15)  NOT NULL,
    pivotal_date      DATE         NOT NULL,   -- ex-date | execution | effective (semantica por tipo)
    declaration_date  DATE,
    record_date       DATE,
    payment_date      DATE,
    source_provider   VARCHAR(20)  NOT NULL,   -- MASSIVE | SEED_MX | ...
    provider_event_id VARCHAR(80),             -- id del proveedor (dedup fuerte cuando existe)
    raw_payload       JSONB,                   -- crudo del proveedor, para auditoria
    synced_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_corporate_action PRIMARY KEY (id),
    -- Dedup natural: un evento por (simbolo, tipo, fecha pivote). Cubre seeds/manual sin provider id.
    CONSTRAINT uq_corporate_action_natural UNIQUE (asset_symbol, action_type, pivotal_date),
    CONSTRAINT ck_corporate_action_type CHECK (action_type IN
        ('SPLIT','DIVIDEND','MERGER','TENDER_OFFER','DELISTING','BANKRUPTCY','SPINOFF')),
    CONSTRAINT ck_corporate_action_status CHECK (status IN
        ('ANNOUNCED','EFFECTIVE','COMPLETED','CANCELLED'))
);

-- Timeline por seguridad ("momentos clave") y lookup por id de proveedor.
CREATE INDEX idx_corporate_action_symbol_date ON corporate_action (asset_symbol, pivotal_date DESC);
CREATE INDEX idx_corporate_action_provider_event ON corporate_action (provider_event_id);
