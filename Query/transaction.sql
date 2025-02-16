CREATE TABLE IF NOT EXISTS public.transaction (
    transaction_id uuid NOT NULL DEFAULT uuid_generate_v4(),     -- Identificador único de la transacción
    user_id uuid NOT NULL,                                       -- Relación con la tabla de usuarios
    asset_symbol VARCHAR(10) NOT NULL,                          -- Símbolo del activo (e.g., BTC, ETH, AAPL)
    asset_type VARCHAR(20) NOT NULL,                            -- Tipo de activo (CRYPTO, STOCK, FOREX, etc.)
    transaction_type VARCHAR(10) NOT NULL CHECK (transaction_type IN ('BUY', 'SELL')), -- Tipo de transacción
    quantity NUMERIC(18, 8) NOT NULL,                           -- Cantidad comprada o vendida
    price_per_unit NUMERIC(18, 8) NOT NULL,                     -- Precio por unidad (obtenido desde la API al registrar la transacción)
    total_value NUMERIC(18, 2) NOT NULL,                        -- Valor total (quantity * price_per_unit)
    transaction_date TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Fecha y hora de la transacción
    fee NUMERIC(18, 2) DEFAULT 0,                               -- Comisión de la transacción
    price_at_transaction NUMERIC(18, 8),                        -- Precio del activo al momento de la transacción (extraído de la API)
    notes TEXT,                                                 -- Notas opcionales sobre la transacción
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Registro de creación
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Registro de actualización
    PRIMARY KEY (transaction_id),
    FOREIGN KEY (user_id) REFERENCES public.users (id) ON DELETE CASCADE -- Relación con tabla de usuarios
);
