# Refresh Token Security - Redis First

> Estado vigente del proyecto al 16 de marzo de 2026.
>
> El flujo activo de `login`, `refresh` y `logout` usa Redis como almacenamiento operativo de refresh tokens. La tabla SQL `refresh_tokens` ya no es la fuente de verdad.

---

## 1. Decision vigente

La implementacion actual es:

- `accessToken`: JWT emitido por `TokenIssuerPort`
- `refreshToken`: valor plano entregado al cliente
- hash del refresh token: HMAC-SHA-256 calculado por `RefreshTokenHashService`
- almacenamiento operativo del refresh token: Redis, via `RefreshTokenStoreService`
- metadata de sesion: PostgreSQL, via tabla `sessions`
- auditoria: PostgreSQL, via tabla `audit_logs`

## 2. Fuente de verdad

### Redis

Redis es la fuente de verdad para:

- existencia del refresh token
- relacion token -> usuario
- relacion token -> sesion
- estado `revoked`
- TTL efectivo del refresh token

Codigo relevante:

- `src/main/java/com/mx/cryptomonitor/user/application/service/RefreshTokenStoreService.java`
- `src/main/java/com/mx/cryptomonitor/user/application/service/AuthService.java`
- `src/main/java/com/mx/cryptomonitor/user/application/service/TokenService.java`

### PostgreSQL

PostgreSQL es la fuente de verdad para:

- `sessions.login_time`
- `sessions.logout_time`
- `sessions.is_active`
- `audit_logs.event_type`
- `audit_logs.description`
- `audit_logs.event_timestamp`

Codigo relevante:

- `src/main/java/com/mx/cryptomonitor/user/domain/model/Session.java`
- `src/main/java/com/mx/cryptomonitor/user/domain/repository/SessionRepository.java`
- `src/main/java/com/mx/cryptomonitor/user/application/service/AuditLogService.java`

## 3. Que significa para `refresh_tokens`

La tabla SQL `refresh_tokens`:

- no participa en el flujo activo de `login`
- no participa en el flujo activo de `refresh`
- no participa en el flujo activo de `logout`
- puede quedar vacia aunque el sistema este funcionando correctamente

Conclusiones operativas:

1. Que `refresh_tokens` este vacia no implica error.
2. El lugar correcto para verificar refresh tokens activos es Redis.
3. Para verificar logout correcto hay que inspeccionar:
   - `sessions.logout_time`
   - `sessions.is_active`
   - `audit_logs.event_type`

## 4. Flujo actual de login

Archivo principal:

- `src/main/java/com/mx/cryptomonitor/user/application/service/AuthService.java`

Secuencia:

1. `AuthService.login(...)` autentica credenciales.
2. Crea una fila en `sessions` con:
   - `login_time`
   - `is_active = true`
   - `refresh_token_id = null`
3. Genera refresh token plano.
4. Calcula hash HMAC-SHA-256.
5. Guarda el hash y metadata en Redis mediante `RefreshTokenStoreService.store(...)`.
6. Emite JWT de acceso.
7. Registra `AUTH_LOGIN_SUCCESS` en `audit_logs`.

## 5. Flujo actual de refresh

El refresh token recibido:

1. se hashea otra vez
2. se busca en Redis
3. se valida `revoked`
4. se valida expiracion por TTL
5. si es valido, se emite nuevo access token

No se consulta `refresh_tokens` SQL en este flujo.

## 6. Flujo actual de logout

Secuencia:

1. El frontend envia `POST /api/v1/auth/logout`
2. Incluye header `X-Refresh-Token`
3. `AuthService.logout(...)` busca el token en Redis
4. Marca el token como revocado en Redis
5. Busca la sesion asociada en `sessions`
6. Actualiza:
   - `logout_time`
   - `is_active = false`
7. Registra `AUTH_LOGOUT_SUCCESS` en `audit_logs`

Validaciones esperadas despues de un logout exitoso:

- `sessions.logout_time` con timestamp
- `sessions.is_active = false`
- `audit_logs.event_type = AUTH_LOGOUT_SUCCESS`

## 7. Estructura en Redis

Prefijos actuales:

- `auth:rt:{tokenHash}`
- `auth:rt:user:{userId}`

Campos persistidos por token:

- `tokenId`
- `userId`
- `sessionId`
- `revoked`
- `ipAddress`
- `userAgent`

Esto permite:

- revocacion individual
- revocacion masiva por usuario
- trazabilidad de la sesion asociada

## 8. Seguridad aplicada

El diseno Redis-first mantiene estos controles:

- el refresh token nunca se busca por valor plano persistido
- el valor operativo persistido es el hash HMAC-SHA-256
- la revocacion es inmediata
- la expiracion la gobierna Redis por TTL
- la sesion y la auditoria siguen en PostgreSQL

## 9. Componentes legacy

Estos componentes pueden seguir existiendo en el codigo, pero no son la ruta activa:

- `src/main/java/com/mx/cryptomonitor/user/domain/model/RefreshToken.java`
- `src/main/java/com/mx/cryptomonitor/user/domain/repository/RefreshTokenRepository.java`

Estado actual:

- se consideran legacy
- no deben usarse como referencia operativa para login/logout
- no deben usarse para concluir si el refresh token flow "funciona" o "falla"

## 10. Regla de soporte y debugging

Si un usuario reporta problema en auth/session, revisar en este orden:

1. `audit_logs`
2. `sessions`
3. Redis
4. CORS y llamada real del frontend a `/api/v1/auth/logout`

No usar `refresh_tokens` SQL como criterio primario de diagnostico.

## 11. Decision de arquitectura

Se mantiene formalmente la politica:

- Redis-first para refresh tokens
- PostgreSQL para sesiones y auditoria
- `refresh_tokens` SQL no es fuente de verdad

Si en el futuro se requiere trazabilidad dual, se puede agregar persistencia espejo en SQL, pero eso seria una capacidad adicional de auditoria, no el mecanismo primario de autenticacion.
