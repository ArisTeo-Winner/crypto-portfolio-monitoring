-- ADR-0025 F1: estado de elegibilidad de dividendos por simbolo.
-- Resultado PERSISTIDO del descubrimiento empirico (ADR-0024): lo fija DividendSyncService tras
-- cada sync (PAYS si el proveedor devolvio eventos, NONE si vacio). Un simbolo sin fila = UNKNOWN.
-- ERROR (proveedor caido/sin cuota, no concluyente) se puebla en la Fase 2. Una fila por simbolo
-- (normalizado, igual que corporate_action.asset_symbol).
CREATE TABLE asset_dividend_status (
    id               UUID         NOT NULL,
    asset_symbol     VARCHAR(20)  NOT NULL,   -- liga logica al security master (asset_catalog.symbol)
    status           VARCHAR(10)  NOT NULL,
    checked_provider VARCHAR(20),             -- proveedor que resolvio el estado (atribucion fina: Fase 2/3)
    last_checked_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_asset_dividend_status PRIMARY KEY (id),
    CONSTRAINT uq_asset_dividend_status_symbol UNIQUE (asset_symbol),
    CONSTRAINT ck_asset_dividend_status CHECK (status IN ('UNKNOWN','PAYS','NONE','ERROR'))
);
