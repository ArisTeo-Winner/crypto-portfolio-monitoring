CREATE TABLE IF NOT EXISTS banxico_cetes_rate (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    term_days    INT           NOT NULL,
    rate         NUMERIC(8,4)  NOT NULL,
    auction_date DATE          NOT NULL,
    fetched_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (term_days, auction_date)
);
CREATE INDEX IF NOT EXISTS idx_banxico_cetes_rate_auction_date
    ON banxico_cetes_rate(auction_date DESC);
