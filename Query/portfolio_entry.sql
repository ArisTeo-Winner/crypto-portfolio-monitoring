CREATE TABLE IF NOT EXISTS public.portfolio_entry (
    portfolio_entry_id uuid NOT NULL DEFAULT uuid_generate_v4(), -- Identificador único de la entrada
    user_id uuid NOT NULL,                                      -- Relación con la tabla de usuarios
    asset_symbol VARCHAR(10) NOT NULL,                         -- Símbolo del activo (BTC, ETH, AAPL, etc.)
    asset_type VARCHAR(20) NOT NULL,                           -- Tipo de activo (CRYPTO, STOCK, FOREX, etc.)
    total_quantity NUMERIC(18, 8) NOT NULL DEFAULT 0,          -- Cantidad neta actual del activo en la cartera
    total_invested NUMERIC(18, 2) NOT NULL DEFAULT 0,          -- Total invertido (suma de compras)
    average_price_per_unit NUMERIC(18, 8) NOT NULL DEFAULT 0,  -- Precio promedio pagado por unidad
    last_transaction_price NUMERIC(18, 8),                     -- Precio de la última transacción registrada
    current_value NUMERIC(18, 2),                              -- Valor actual del activo (calculado en tiempo real)
    total_profit_loss NUMERIC(18, 2),                          -- Ganancia o pérdida total
    last_updated TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Última actualización
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,  -- Registro de creación
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,  -- Registro de actualización
    PRIMARY KEY (portfolio_entry_id),
    FOREIGN KEY (user_id) REFERENCES public.users (id) ON DELETE CASCADE, -- Relación con tabla de usuarios
    UNIQUE (user_id, asset_symbol) -- Una entrada única por usuario y activo
);
