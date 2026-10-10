-- Calendario de festivos/cierres bursatiles por mercado (ADR-0012).
-- early_close NULL = cierre total; no-NULL = medio dia (cierre anticipado a esa hora LOCAL del
-- mercado). Fuente de verdad del backend; el US puede auto-extenderse via proveedor (F4), MX por
-- migracion. Fechas 2026 verificadas contra reglas oficiales NYSE y BMV/BIVA.
CREATE TABLE IF NOT EXISTS market_holiday (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    market        VARCHAR(16) NOT NULL,
    holiday_date  DATE        NOT NULL,
    name          VARCHAR(80) NOT NULL,
    early_close   TIME        NULL,
    UNIQUE (market, holiday_date)
);
CREATE INDEX IF NOT EXISTS idx_market_holiday_market ON market_holiday(market, holiday_date);

-- NYSE/NASDAQ 2026: 10 cierres totales + 2 cierres anticipados (13:00 ET).
INSERT INTO market_holiday (market, holiday_date, name, early_close) VALUES
    ('US_EQUITY', '2026-01-01', 'New Year''s Day', NULL),
    ('US_EQUITY', '2026-01-19', 'Martin Luther King, Jr. Day', NULL),
    ('US_EQUITY', '2026-02-16', 'Washington''s Birthday', NULL),
    ('US_EQUITY', '2026-04-03', 'Good Friday', NULL),
    ('US_EQUITY', '2026-05-25', 'Memorial Day', NULL),
    ('US_EQUITY', '2026-06-19', 'Juneteenth', NULL),
    ('US_EQUITY', '2026-07-03', 'Independence Day (observed)', NULL),
    ('US_EQUITY', '2026-09-07', 'Labor Day', NULL),
    ('US_EQUITY', '2026-11-26', 'Thanksgiving Day', NULL),
    ('US_EQUITY', '2026-12-25', 'Christmas Day', NULL),
    ('US_EQUITY', '2026-11-27', 'Day After Thanksgiving', '13:00'),
    ('US_EQUITY', '2026-12-24', 'Christmas Eve', '13:00');

-- BMV/BIVA 2026: 9 cierres totales (sin cierres anticipados).
INSERT INTO market_holiday (market, holiday_date, name, early_close) VALUES
    ('MX_BMV', '2026-01-01', 'Ano Nuevo', NULL),
    ('MX_BMV', '2026-02-02', 'Dia de la Constitucion', NULL),
    ('MX_BMV', '2026-03-16', 'Natalicio de Benito Juarez', NULL),
    ('MX_BMV', '2026-04-02', 'Jueves Santo', NULL),
    ('MX_BMV', '2026-04-03', 'Viernes Santo', NULL),
    ('MX_BMV', '2026-05-01', 'Dia del Trabajo', NULL),
    ('MX_BMV', '2026-09-16', 'Dia de la Independencia', NULL),
    ('MX_BMV', '2026-11-16', 'Dia de la Revolucion', NULL),
    ('MX_BMV', '2026-12-25', 'Navidad', NULL);
