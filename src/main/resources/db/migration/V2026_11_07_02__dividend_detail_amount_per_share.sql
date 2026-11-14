-- ADR-0029: read-model de ingresos por dividendo del usuario.
-- Denormaliza el dividendo por accion sobre el detalle de la transaccion DIVIDEND, para que
-- "Mis Dividendos" (GET /api/v1/me/dividends) muestre acciones y div/accion sin cruzar a marketdata
-- por fila. Nullable: los dividendos acreditados antes de esta migracion quedan en null (backfill
-- opcional). frequency y distribution_type (columna Tipo completa) llegan en una fase posterior.
ALTER TABLE dividend_detail
    ADD COLUMN amount_per_share NUMERIC(18, 8);
