# Solución: Inconsistencia portfolio_entry vs transaction

> **Problema:** Borrado manual de transacciones en `transaction` dejó registros huérfanos en `portfolio_entry`
>
> **Causa raíz:** Tabla desnormalizada `portfolio_entry` sin sincronización automática

---

## 📋 Tabla de Contenidos

- [1. Diagnóstico del problema](#1-diagnóstico-del-problema)
- [2. Solución inmediata: Limpiar portfolio_entry](#2-solución-inmediata-limpiar-portfolio_entry)
- [3. Solución permanente: Database triggers](#3-solución-permanente-database-triggers)
- [4. Alternativa: Eliminar portfolio_entry (vista calculada)](#4-alternativa-eliminar-portfolio_entry-vista-calculada)
- [5. Comparativa de soluciones](#5-comparativa-de-soluciones)
- [6. Implementación recomendada](#6-implementación-recomendada)

---

## 1. Diagnóstico del problema

### Estado actual de las tablas

**Tabla `transaction`:**
```sql
SELECT 
    asset_symbol,
    COUNT(*) as tx_count,
    SUM(CASE WHEN asset_type = 'CRYPTO' THEN 1 ELSE 0 END) as crypto_count
FROM transaction
WHERE user_id = 'fcc051d0-3de9-483f-a55e-f689dc482dc7'
GROUP BY asset_symbol
ORDER BY asset_symbol;
```

**Resultado (después del borrado manual):**
```
asset_symbol | tx_count | crypto_count
-------------|----------|-------------
AAPL         | 1        | 0
ETH          | 3        | 3
GOOGL        | 1        | 0
HYPE         | 1        | 1
MSFT         | 1        | 0
SOL          | 0        | 0  ← YA NO EXISTE
TRX          | 1        | 1
XMR          | 1        | 1
```

---

**Tabla `portfolio_entry`:**
```sql
SELECT 
    asset_symbol,
    asset_type,
    current_value,
    created_at
FROM portfolio_entry
WHERE user_id = 'fcc051d0-3de9-483f-a55e-f689dc482dc7'
ORDER BY asset_symbol;
```

**Resultado (ANTES del fix):**
```
asset_symbol | asset_type | current_value | created_at
-------------|------------|---------------|----------------------------
AAPL         | STOCK      | 249.56       | 2026-03-20 03:11:39.916
ETH          | CRYPTO     | 2,150.6875   | 2026-03-19 19:11:49.307
GOOGL        | STOCK      | 307.13       | 2026-03-20 08:00:04.150
HYPE         | CRYPTO     | 38.58        | 2026-03-26 19:18:54.682
MSFT         | STOCK      | 389.02       | 2026-03-20 03:12:29.597
SOL          | CRYPTO     | 89.2354      | 2026-03-19 19:17:08.054  ← HUÉRFANO
TRX          | CRYPTO     | 0.30751987   | 2026-03-25 06:52:17.575
XMR          | CRYPTO     | 339.87       | 2026-03-25 20:43:38.532
```

**Problema identificado:**
- `SOL` existe en `portfolio_entry` pero **NO** tiene transacciones en `transaction`
- Es un **registro huérfano** (orphaned record)

---

## 2. Solución inmediata: Limpiar portfolio_entry

### Opción A: Eliminar solo SOL (específico)

```sql
-- Eliminar el registro huérfano de SOL
DELETE FROM portfolio_entry
WHERE user_id = 'fcc051d0-3de9-483f-a55e-f689dc482dc7'
  AND asset_symbol = 'SOL';

-- Verificar
SELECT asset_symbol, current_value 
FROM portfolio_entry
WHERE user_id = 'fcc051d0-3de9-483f-a55e-f689dc482dc7'
ORDER BY asset_symbol;
```

---

### Opción B: Eliminar TODOS los registros huérfanos (general)

```sql
-- Eliminar todos los portfolio_entry que NO tienen transacciones
DELETE FROM portfolio_entry pe
WHERE NOT EXISTS (
    SELECT 1 
    FROM transaction t 
    WHERE t.user_id = pe.user_id 
      AND t.asset_symbol = pe.asset_symbol
);

-- Verificar cuántos se eliminaron
-- (deberías ver "1 row(s) deleted" si solo SOL era huérfano)
```

---

### Opción C: Recalcular TODO desde cero (más seguro)

```sql
-- 1. Eliminar TODAS las entradas del usuario
DELETE FROM portfolio_entry
WHERE user_id = 'fcc051d0-3de9-483f-a55e-f689dc482dc7';

-- 2. Recalcular desde las transacciones
INSERT INTO portfolio_entry (
    portfolio_entry_id,
    user_id,
    email,
    asset_symbol,
    asset_type,
    average_price_per_unit,
    current_value,
    last_updated,
    created_at
)
SELECT 
    gen_random_uuid() AS portfolio_entry_id,
    t.user_id,
    u.email,
    t.asset_symbol,
    t.asset_type,
    AVG(t.price_per_unit) AS average_price_per_unit,
    -- Para current_value necesitarías hacer join con tabla de precios actuales
    -- Por ahora usar el último precio conocido
    (SELECT price_per_unit FROM transaction 
     WHERE user_id = t.user_id AND asset_symbol = t.asset_symbol 
     ORDER BY transaction_date DESC LIMIT 1) AS current_value,
    NOW() AS last_updated,
    NOW() AS created_at
FROM transaction t
JOIN users u ON t.user_id = u.id
WHERE t.user_id = 'fcc051d0-3de9-483f-a55e-f689dc482dc7'
GROUP BY t.user_id, u.email, t.asset_symbol, t.asset_type;

-- 3. Verificar
SELECT asset_symbol, current_value 
FROM portfolio_entry
WHERE user_id = 'fcc051d0-3de9-483f-a55e-f689dc482dc7'
ORDER BY asset_symbol;
```

**Resultado esperado:**
```
asset_symbol | current_value
-------------|---------------
AAPL         | 249.56
ETH          | 2,150.6875
GOOGL        | 307.13
HYPE         | 38.58
MSFT         | 389.02
TRX          | 0.30751987
XMR          | 339.87
```
(SOL ya NO aparece)

---

## 3. Solución permanente: Database triggers

### Arquitectura con triggers

```
transaction table (SINGLE SOURCE OF TRUTH)
    ↓
    ├─ INSERT trigger → UPDATE portfolio_entry
    ├─ UPDATE trigger → UPDATE portfolio_entry
    └─ DELETE trigger → UPDATE portfolio_entry
    ↓
portfolio_entry table (MATERIALIZED VIEW)
```

---

### Trigger 1: Después de INSERT

```sql
CREATE OR REPLACE FUNCTION sync_portfolio_entry_on_insert()
RETURNS TRIGGER AS $$
BEGIN
    -- Insertar o actualizar portfolio_entry
    INSERT INTO portfolio_entry (
        portfolio_entry_id,
        user_id,
        email,
        asset_symbol,
        asset_type,
        average_price_per_unit,
        current_value,
        last_updated,
        created_at
    )
    SELECT 
        gen_random_uuid(),
        NEW.user_id,
        (SELECT email FROM users WHERE id = NEW.user_id),
        NEW.asset_symbol,
        NEW.asset_type,
        AVG(price_per_unit),
        (SELECT price_per_unit FROM transaction 
         WHERE user_id = NEW.user_id AND asset_symbol = NEW.asset_symbol 
         ORDER BY transaction_date DESC LIMIT 1),
        NOW(),
        NOW()
    FROM transaction
    WHERE user_id = NEW.user_id AND asset_symbol = NEW.asset_symbol
    GROUP BY user_id, asset_symbol, asset_type
    
    -- Si ya existe, actualizar
    ON CONFLICT (user_id, asset_symbol) 
    DO UPDATE SET
        average_price_per_unit = EXCLUDED.average_price_per_unit,
        current_value = EXCLUDED.current_value,
        last_updated = NOW();
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_sync_portfolio_on_insert
AFTER INSERT ON transaction
FOR EACH ROW
EXECUTE FUNCTION sync_portfolio_entry_on_insert();
```

---

### Trigger 2: Después de DELETE

```sql
CREATE OR REPLACE FUNCTION sync_portfolio_entry_on_delete()
RETURNS TRIGGER AS $$
BEGIN
    -- Verificar si quedan transacciones del mismo activo
    IF NOT EXISTS (
        SELECT 1 FROM transaction 
        WHERE user_id = OLD.user_id 
          AND asset_symbol = OLD.asset_symbol
    ) THEN
        -- Si no quedan transacciones, eliminar de portfolio_entry
        DELETE FROM portfolio_entry
        WHERE user_id = OLD.user_id 
          AND asset_symbol = OLD.asset_symbol;
    ELSE
        -- Si quedan transacciones, recalcular
        UPDATE portfolio_entry
        SET 
            average_price_per_unit = (
                SELECT AVG(price_per_unit) 
                FROM transaction 
                WHERE user_id = OLD.user_id AND asset_symbol = OLD.asset_symbol
            ),
            current_value = (
                SELECT price_per_unit FROM transaction 
                WHERE user_id = OLD.user_id AND asset_symbol = OLD.asset_symbol 
                ORDER BY transaction_date DESC LIMIT 1
            ),
            last_updated = NOW()
        WHERE user_id = OLD.user_id 
          AND asset_symbol = OLD.asset_symbol;
    END IF;
    
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_sync_portfolio_on_delete
AFTER DELETE ON transaction
FOR EACH ROW
EXECUTE FUNCTION sync_portfolio_entry_on_delete();
```

---

### Trigger 3: Después de UPDATE

```sql
CREATE OR REPLACE FUNCTION sync_portfolio_entry_on_update()
RETURNS TRIGGER AS $$
BEGIN
    -- Recalcular portfolio_entry cuando cambia precio o cantidad
    UPDATE portfolio_entry
    SET 
        average_price_per_unit = (
            SELECT AVG(price_per_unit) 
            FROM transaction 
            WHERE user_id = NEW.user_id AND asset_symbol = NEW.asset_symbol
        ),
        current_value = (
            SELECT price_per_unit FROM transaction 
            WHERE user_id = NEW.user_id AND asset_symbol = NEW.asset_symbol 
            ORDER BY transaction_date DESC LIMIT 1
        ),
        last_updated = NOW()
    WHERE user_id = NEW.user_id 
      AND asset_symbol = NEW.asset_symbol;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_sync_portfolio_on_update
AFTER UPDATE ON transaction
FOR EACH ROW
WHEN (OLD.price_per_unit IS DISTINCT FROM NEW.price_per_unit 
   OR OLD.quantity IS DISTINCT FROM NEW.quantity)
EXECUTE FUNCTION sync_portfolio_entry_on_update();
```

---

### Probar los triggers

```sql
-- Test 1: Insertar nueva transacción
INSERT INTO transaction (
    user_id, asset_symbol, asset_type, 
    price_per_unit, quantity, transaction_date
) VALUES (
    'fcc051d0-3de9-483f-a55e-f689dc482dc7',
    'BTC', 'CRYPTO',
    71234.56, 0.5, NOW()
);

-- Verificar que portfolio_entry se actualizó automáticamente
SELECT * FROM portfolio_entry 
WHERE asset_symbol = 'BTC' 
  AND user_id = 'fcc051d0-3de9-483f-a55e-f689dc482dc7';

-- Test 2: Eliminar transacción (última de BTC)
DELETE FROM transaction
WHERE user_id = 'fcc051d0-3de9-483f-a55e-f689dc482dc7'
  AND asset_symbol = 'BTC';

-- Verificar que portfolio_entry también se eliminó
SELECT * FROM portfolio_entry 
WHERE asset_symbol = 'BTC' 
  AND user_id = 'fcc051d0-3de9-483f-a55e-f689dc482dc7';
-- Debería retornar 0 filas
```

---

## 4. Alternativa: Eliminar portfolio_entry (vista calculada)

### Opción radical: Usar vista materializada

En lugar de mantener `portfolio_entry` sincronizada, calcular dinámicamente:

```sql
-- Eliminar tabla física
DROP TABLE portfolio_entry;

-- Crear vista materializada
CREATE MATERIALIZED VIEW portfolio_entry AS
SELECT 
    gen_random_uuid() AS portfolio_entry_id,
    t.user_id,
    u.email,
    t.asset_symbol,
    t.asset_type,
    AVG(t.price_per_unit) AS average_price_per_unit,
    -- Calcular current_value desde tabla de precios en tiempo real
    COALESCE(p.current_price, 
        (SELECT price_per_unit FROM transaction 
         WHERE user_id = t.user_id AND asset_symbol = t.asset_symbol 
         ORDER BY transaction_date DESC LIMIT 1)
    ) AS current_value,
    SUM(t.quantity) AS total_quantity,
    NOW() AS last_updated,
    MIN(t.transaction_date) AS created_at
FROM transaction t
JOIN users u ON t.user_id = u.id
LEFT JOIN asset_prices p ON t.asset_symbol = p.symbol  -- Tabla de precios actuales
GROUP BY t.user_id, u.email, t.asset_symbol, t.asset_type, p.current_price;

-- Crear índice para performance
CREATE UNIQUE INDEX idx_portfolio_entry_user_asset 
ON portfolio_entry(user_id, asset_symbol);

-- Refrescar la vista cuando sea necesario
REFRESH MATERIALIZED VIEW CONCURRENTLY portfolio_entry;
```

**Ventajas:**
- ✅ NUNCA se desincroniza (se recalcula desde `transaction`)
- ✅ No requiere triggers
- ✅ Single source of truth

**Desventajas:**
- ❌ Requiere `REFRESH MATERIALIZED VIEW` periódicamente
- ❌ Puede ser lento con muchas transacciones (optimizar con índices)

---

### Job programado para refrescar la vista

```java
@Component
public class PortfolioRefreshJob {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Refrescar cada 5 minutos
    @Scheduled(fixedRate = 300000)
    public void refreshPortfolioView() {
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY portfolio_entry");
        log.info("Portfolio view refreshed at {}", OffsetDateTime.now());
    }
}
```

---

## 5. Comparativa de soluciones

| Solución | Pros | Contras | Recomendación |
|----------|------|---------|---------------|
| **A. Triggers automáticos** | ✅ Sincronización automática<br>✅ Tiempo real<br>✅ No requiere cambios en código | ❌ Complejidad en DB<br>❌ Difícil de debuggear<br>❌ Performance impact en writes | ⭐⭐⭐⭐ Buena para producción |
| **B. Vista materializada** | ✅ NUNCA se desincroniza<br>✅ Simple de entender<br>✅ No requiere triggers | ❌ Requiere refresh periódico<br>❌ Puede ser lento con muchos datos | ⭐⭐⭐⭐⭐ **MEJOR OPCIÓN** |
| **C. Eliminar portfolio_entry** | ✅ Sin duplicación de datos<br>✅ Siempre consistente | ❌ Query complejo en cada request<br>❌ Lento en dashboards | ⭐⭐ Solo para MVP |
| **D. Sincronización manual** | ✅ Control total | ❌ Propenso a errores<br>❌ Requiere disciplina | ❌ NO USAR |

---

## 6. Implementación recomendada

### Paso 1: Limpiar datos actuales (HOY)

```sql
-- Ejecutar AHORA en DBeaver
DELETE FROM portfolio_entry
WHERE user_id = 'fcc051d0-3de9-483f-a55e-f689dc482dc7'
  AND asset_symbol = 'SOL';

-- Verificar
SELECT asset_symbol, current_value 
FROM portfolio_entry
WHERE user_id = 'fcc051d0-3de9-483f-a55e-f689dc482dc7'
ORDER BY asset_symbol;
```

---

### Paso 2: Implementar triggers (SEMANA 1)

```sql
-- Ejecutar los 3 triggers documentados arriba
-- 1. sync_portfolio_entry_on_insert
-- 2. sync_portfolio_entry_on_delete
-- 3. sync_portfolio_entry_on_update
```

---

### Paso 3: Agregar constraint de integridad (SEMANA 2)

```sql
-- Agregar foreign key para prevenir huérfanos
ALTER TABLE portfolio_entry
ADD CONSTRAINT fk_portfolio_user 
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- Agregar constraint único
ALTER TABLE portfolio_entry
ADD CONSTRAINT uniq_user_asset 
UNIQUE (user_id, asset_symbol);
```

---

### Paso 4: Monitoreo de consistencia (SEMANA 3)

```sql
-- Query para detectar inconsistencias
CREATE OR REPLACE FUNCTION check_portfolio_consistency()
RETURNS TABLE (
    user_id UUID,
    asset_symbol VARCHAR,
    issue TEXT
) AS $$
BEGIN
    -- Caso 1: portfolio_entry sin transactions
    RETURN QUERY
    SELECT 
        pe.user_id,
        pe.asset_symbol,
        'Orphaned entry (no transactions)' AS issue
    FROM portfolio_entry pe
    WHERE NOT EXISTS (
        SELECT 1 FROM transaction t 
        WHERE t.user_id = pe.user_id 
          AND t.asset_symbol = pe.asset_symbol
    );
    
    -- Caso 2: transactions sin portfolio_entry
    RETURN QUERY
    SELECT 
        t.user_id,
        t.asset_symbol,
        'Missing portfolio entry' AS issue
    FROM transaction t
    WHERE NOT EXISTS (
        SELECT 1 FROM portfolio_entry pe 
        WHERE pe.user_id = t.user_id 
          AND pe.asset_symbol = t.asset_symbol
    )
    GROUP BY t.user_id, t.asset_symbol;
END;
$$ LANGUAGE plpgsql;

-- Ejecutar check periódicamente
SELECT * FROM check_portfolio_consistency();
```

---

### Paso 5: Job de reconciliación (SEMANA 4)

```java
@Component
public class PortfolioReconciliationJob {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Ejecutar cada noche a las 3 AM
    @Scheduled(cron = "0 0 3 * * ?")
    public void reconcilePortfolio() {
        log.info("Starting portfolio reconciliation...");
        
        // Detectar inconsistencias
        List<Map<String, Object>> issues = jdbcTemplate.queryForList(
            "SELECT * FROM check_portfolio_consistency()"
        );
        
        if (!issues.isEmpty()) {
            log.error("Found {} portfolio inconsistencies", issues.size());
            // Enviar alerta a Slack/email
            alertService.sendAlert("Portfolio inconsistencies detected", issues);
            
            // Auto-fix: recalcular desde transactions
            jdbcTemplate.execute(
                "DELETE FROM portfolio_entry WHERE NOT EXISTS " +
                "(SELECT 1 FROM transaction t WHERE t.user_id = portfolio_entry.user_id " +
                "AND t.asset_symbol = portfolio_entry.asset_symbol)"
            );
        } else {
            log.info("Portfolio is consistent");
        }
    }
}
```

---

## 📚 Resumen ejecutivo

### Problema identificado:
- ✅ `transaction` es la **fuente de verdad**
- ❌ `portfolio_entry` es una **tabla desnormalizada** que quedó desincronizada
- ❌ Al borrar manualmente de `transaction`, `portfolio_entry` NO se actualizó

### Solución inmediata (HOY):
```sql
DELETE FROM portfolio_entry
WHERE user_id = 'fcc051d0-3de9-483f-a55e-f689dc482dc7'
  AND asset_symbol = 'SOL';
```

### Solución permanente (PRÓXIMAS SEMANAS):
1. Implementar **triggers** para auto-sincronización
2. Agregar **constraints** de integridad
3. Crear **job de reconciliación** nocturno
4. Considerar migrar a **vista materializada**

### Lección aprendida:
**NUNCA editar manualmente tablas desnormalizadas**. Siempre editar la fuente de verdad (`transaction`) y dejar que los triggers/jobs actualicen las agregaciones.

---

## 📄 Licencia

Documentación técnica — Portfolio Tracker  
Stack: PostgreSQL · PL/pgSQL · Spring Boot  
Versión: 1.0 · Marzo 2026
