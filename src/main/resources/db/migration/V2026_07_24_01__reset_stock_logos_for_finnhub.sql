-- Las URLs de Finnhub no son deterministas y no se pueden hardcodear en SQL.
-- El proximo sync (o forceFullSync) repuebla los logos de STOCK via Finnhub /stock/profile2.
-- ETF y GOVERNMENT_BOND en USD conservan su logo de CompaniesLogo.
UPDATE asset_catalog SET logo_url = NULL WHERE asset_type = 'STOCK';
