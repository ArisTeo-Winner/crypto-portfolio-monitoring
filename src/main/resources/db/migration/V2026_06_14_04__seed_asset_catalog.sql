INSERT INTO asset_catalog (symbol, name, asset_type, logo_url, exchange, currency, market_cap, is_popular)
VALUES
  -- Cryptos
  ('BTC',    'Bitcoin',           'CRYPTO',          NULL, NULL,          'USD', 1900000, FALSE),
  ('ETH',    'Ethereum',          'CRYPTO',          NULL, NULL,          'USD',  460000, FALSE),
  ('USDT',   'Tether',            'CRYPTO',          NULL, NULL,          'USD',  130000, FALSE),
  ('BNB',    'BNB',               'CRYPTO',          NULL, NULL,          'USD',   88000, FALSE),
  ('XRP',    'XRP',               'CRYPTO',          NULL, NULL,          'USD',  140000, FALSE),
  ('USDC',   'USD Coin',          'CRYPTO',          NULL, NULL,          'USD',   61000, FALSE),

  -- Stocks
  ('AAPL',   'Apple Inc.',                 'STOCK', NULL, 'NASDAQGS', 'USD', 3300000, TRUE),
  ('NVDA',   'NVIDIA Corporation',         'STOCK', NULL, 'NASDAQGS', 'USD', 3200000, TRUE),
  ('MSFT',   'Microsoft Corporation',      'STOCK', NULL, 'NASDAQGS', 'USD', 3100000, TRUE),
  ('GOOGL',  'Alphabet Inc. Class A',      'STOCK', NULL, 'NASDAQGS', 'USD', 2100000, TRUE),
  ('META',   'Meta Platforms Inc.',        'STOCK', NULL, 'NASDAQGS', 'USD', 1500000, TRUE),
  ('GOOG',   'Alphabet Inc. Class C',      'STOCK', NULL, 'NASDAQGS', 'USD', 2100000, FALSE),
  ('AMZN',   'Amazon.com Inc.',            'STOCK', NULL, 'NASDAQGS', 'USD', 2000000, FALSE),
  ('TSLA',   'Tesla Inc.',                 'STOCK', NULL, 'NASDAQGS', 'USD',  800000, FALSE),
  ('CRCL',   'Circle Internet Financial',  'STOCK', NULL, 'NYSE',     'USD',   45000, FALSE),

  -- ETFs
  ('VOO',  'Vanguard S&P 500 ETF',                  'ETF', NULL, 'NYSE', 'USD', 580000, TRUE),
  ('IVV',  'iShares Core S&P 500 ETF',              'ETF', NULL, 'NYSE', 'USD', 570000, TRUE),
  ('SPY',  'SPDR S&P 500 ETF Trust',                'ETF', NULL, 'NYSE', 'USD', 560000, TRUE),
  ('VTI',  'Vanguard Total Stock Market ETF',       'ETF', NULL, 'NYSE', 'USD', 430000, TRUE),
  ('QQQ',  'Invesco QQQ Trust Series 1',            'ETF', NULL, 'NASDAQGS', 'USD', 310000, TRUE),
  ('VEA',  'Vanguard FTSE Developed Markets ETF',   'ETF', NULL, 'NYSE', 'USD', 130000, FALSE),
  ('VUG',  'Vanguard Growth ETF',                   'ETF', NULL, 'NYSE', 'USD', 130000, FALSE),
  ('GLD',  'SPDR Gold Shares',                      'ETF', NULL, 'NYSE', 'USD',  75000, FALSE),
  ('BND',  'Vanguard Total Bond Market ETF',        'ETF', NULL, 'NASDAQGS', 'USD',  95000, FALSE),
  ('SCHD', 'Schwab US Dividend Equity ETF',         'ETF', NULL, 'NYSE', 'USD',  70000, FALSE),
  ('IBIT', 'iShares Bitcoin Trust ETF',             'ETF', NULL, 'NASDAQGS', 'USD',  60000, FALSE),

  -- Government bonds MX
  ('CETES28',   'CETES 28 dias',     'GOVERNMENT_BOND', NULL, 'cetesdirecto', 'MXN', NULL, FALSE),
  ('CETES91',   'CETES 91 dias',     'GOVERNMENT_BOND', NULL, 'cetesdirecto', 'MXN', NULL, FALSE),
  ('CETES182',  'CETES 182 dias',    'GOVERNMENT_BOND', NULL, 'cetesdirecto', 'MXN', NULL, FALSE),
  ('CETES364',  'CETES 364 dias',    'GOVERNMENT_BOND', NULL, 'cetesdirecto', 'MXN', NULL, FALSE),
  ('UDIBONO',   'UDIBONO',           'GOVERNMENT_BOND', NULL, 'cetesdirecto', 'MXN', NULL, FALSE),
  ('BONDM',     'Bonos M',           'GOVERNMENT_BOND', NULL, 'cetesdirecto', 'MXN', NULL, FALSE),

  -- Government bonds USA
  ('TLT',  'iShares 20+ Year Treasury Bond ETF', 'GOVERNMENT_BOND', NULL, NULL, 'USD', NULL, FALSE),
  ('IEF',  'iShares 7-10 Year Treasury Bond ETF','GOVERNMENT_BOND', NULL, NULL, 'USD', NULL, FALSE),
  ('SHY',  'iShares 1-3 Year Treasury Bond ETF', 'GOVERNMENT_BOND', NULL, NULL, 'USD', NULL, FALSE),
  ('GOVT', 'iShares US Treasury Bond ETF',        'GOVERNMENT_BOND', NULL, NULL, 'USD', NULL, FALSE),

  -- Indices
  ('^GSPC', 'S&P 500',    'INDEX', NULL, NULL, 'USD', NULL, FALSE),
  ('^IXIC', 'NASDAQ Composite', 'INDEX', NULL, NULL, 'USD', NULL, FALSE),
  ('^DJI',  'Dow Jones Industrial Average', 'INDEX', NULL, NULL, 'USD', NULL, FALSE)

ON CONFLICT (symbol) DO NOTHING;
