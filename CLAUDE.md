# CLAUDE.md — crypto-portfolio-monitoring (backend)

## Stack

| Tecnología | Versión |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.11 |
| Spring Modulith | 1.4.1 |
| Spring Security | incluida en Boot |
| Spring Data JPA | incluida en Boot |
| Spring Data Redis | incluida en Boot (Lettuce) |
| PostgreSQL | 16 (prod), Testcontainers en tests |
| Redis | 7 (prod), Testcontainers en tests |
| JJWT | 0.11.5 |
| Lombok | 1.18.32 |
| MapStruct | 1.5.5.Final |
| Testcontainers | BOM gestionado por Boot |
| Spotless / Google Java Format | 1.17.0 |

---

## Estructura de módulos

```
com.mx.cryptomonitor
├── user/          # autenticación, sesiones, roles, auditoría
├── portfolio/     # portafolios por usuario
├── transaction/   # compras, ventas, transfers
├── asset/         # búsqueda de activos
├── marketdata/    # precios crypto/stock externos
└── shared/        # infraestructura común (rate limit, cache, tracing)
```

Cada módulo sigue **arquitectura hexagonal**:
```
<modulo>/
├── application/
│   ├── dto/request|response/   # records inmutables
│   ├── mapper/                  # MapStruct
│   ├── port/in|out/             # interfaces del dominio
│   └── service/                 # casos de uso (@Service)
├── domain/
│   ├── model/                   # @Entity, enums
│   ├── port/                    # puertos de dominio
│   └── repository/              # interfaces JPA
└── infrastructure/
    ├── configuration/
    ├── inbound/rest/            # @RestController
    └── security/
```

---

## Comandos frecuentes

```bash
# Compilar sin tests
./mvnw clean package -DskipTests

# Ejecutar todos los tests
./mvnw test

# Formatear código (Google Java Format)
./mvnw spotless:apply

# Verificar formato sin modificar
./mvnw spotless:check

# Cobertura JaCoCo
./mvnw test jacoco:report
# reporte en target/site/jacoco/index.html
```

---

## Reglas de sesiones — OBLIGATORIAS

Estas reglas aplican cada vez que se programa cualquier flujo de login,
logout, refresh, OAuth2, revocación o listado de sesiones.

### 1. Redis es la fuente de verdad para refresh tokens

Usar siempre `RefreshTokenStoreService` (nunca guardar refresh tokens en
la tabla `refresh_tokens` de la DB; esa entidad es legado).

```java
// CORRECTO — al hacer login o emitir tokens
refreshTokenStoreService.store(
    rawRefreshToken,
    user.getId(),
    session.getSessionId(),
    expiresAt,
    ipAddress,    // OBLIGATORIO
    userAgent);   // OBLIGATORIO
```

Claves Redis que gestiona el servicio:
- `auth:rt:{tokenHash}` — hash con campos: tokenId, userId, sessionId, revoked, ipAddress, userAgent
- `auth:rt:user:{userId}` — set de hashes del usuario (índice para listado y revocación)

### 2. IP y User-Agent son campos obligatorios en toda sesión

**Siempre** capturar ambos desde `HttpServletRequest` antes de almacenar
o loggear. Nunca almacenar `null` como valor intencional; usar `""` si
realmente no hay valor.

```java
// Patrón canónico de extracción (tomado de AuthService / issueTokensForUser)
String ipAddress = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
    .filter(s -> !s.isBlank())
    .map(xff -> xff.split(",")[0].trim())
    .orElseGet(request::getRemoteAddr);

String userAgent = Optional.ofNullable(request.getHeader("User-Agent"))
    .orElse("");
```

No inventar un método propio: si la lógica de extracción necesita cambiar,
hacerlo en `AuditLogService.extractClientIp()` y reutilizar.

### 3. Toda sesión debe tener una entrada en la tabla `sessions`

Al crear una sesión, persistir primero en DB (`sessionRepository.save(session)`)
para obtener el `sessionId` (UUID generado por Hibernate), y **luego** llamar
a `refreshTokenStoreService.store(...)` pasando ese `sessionId`.

```java
// Orden OBLIGATORIO
Session session = new Session();
session.setUser(user);
session.setLoginTime(OffsetDateTime.now());
session.setActive(true);
session.setRefreshTokenId(null); // Redis es la fuente de verdad
sessionRepository.save(session);  // 1. obtener sessionId

refreshTokenStoreService.store(   // 2. guardar en Redis con ese sessionId
    refreshToken, user.getId(), session.getSessionId(),
    expiresAt, ipAddress, userAgent);
```

### 4. El access token debe incluir `session_id` como claim

```java
// JwtTokenUtil.generateAccessToken — nunca omitir el claim session_id
claims.put("session_id", sessionId.toString());
```

El `JwtRequestFilter` valida que la sesión esté activa en DB usando ese claim.
Si se genera un token sin él, el filtro rechazará todas las peticiones con 401.

### 5. Audit log en cada evento de autenticación

Llamar a `AuditLogService.log()` en éxito Y en fracaso. Los eventos
disponibles están en `AuditEventType`:

```
AUTH_LOGIN_SUCCESS / AUTH_LOGIN_FAILED
AUTH_LOGOUT_SUCCESS / AUTH_LOGOUT_FAILED
TOKEN_REFRESH_SUCCESS / TOKEN_REFRESH_FAILED
```

```java
// Patrón: try-catch en el servicio de autenticación
try {
    // ... lógica
    auditLogService.log(AuditEventType.AUTH_LOGIN_SUCCESS, "...", user.getId(), request);
} catch (RuntimeException ex) {
    auditLogService.log(AuditEventType.AUTH_LOGIN_FAILED, "...", userId, request);
    throw ex;
}
```

### 6. El endpoint de sesiones vive bajo `/api/v1/me/sessions`

- `GET  /api/v1/me/sessions`              → `UserSessionController.listSessions()`
- `DELETE /api/v1/me/sessions/{sessionId}` → `UserSessionController.revokeSession()`

Requiere `ROLE_USER`. Está declarado en `SecurityConfig`:
```java
authorizeRequests.requestMatchers("/api/v1/me/sessions/**").hasRole("USER");
```

No permitir que un usuario revoque su propia sesión activa (comparar
`sessionId` del JWT con el `sessionId` del path).

---

## Convenciones de seguridad

- **Errores HTTP** → RFC 9457 Problem Detail JSON (`application/problem+json`).
  Usar `JwtAuthenticationEntryPoint` (401) y `CustomAccessDeniedHandler` (403).
  No escribir `ResponseEntity.status(401).body("texto plano")`.

- **Rate limiting** → extender `AbstractRequestRateLimiter` en
  `shared/infrastructure/security/ratelimit/`. Respaldado por
  `RedisRateLimitStore` en prod e `InMemoryRateLimitStore` en tests.
  Añadir propiedad `security.<nombre>-rate-limit.enabled` para poder
  desactivarlo en tests (`security.login-rate-limit.enabled=false`).

- **Passwords** → solo BCrypt vía el bean `PasswordEncoder` de
  `PasswordEncoderConfig`. Nunca SHA-*, MD5 ni texto plano.

- **Secretos en logs** → ningún token, password ni hash puede aparecer
  en logs. Verificar con los tests `AuthNoSecretsInLogsUnitTest` y
  `SensitiveRequestToStringRedactionUnitTest` antes de mergear.

- **CORS** → configurado en `SecurityConfig.corsConfigurationSource()`.
  El origen permitido viene de `${app.frontend-base-url}`. No hardcodear URLs.

---

## Convenciones de arquitectura

- Un módulo **no puede importar clases internas de otro módulo**.
  La comunicación entre módulos es solo a través de interfaces de puerto o eventos.
  Validar con los tests de `architecture/` (ArchUnit).

- Los **DTOs de request/response** son `record`s inmutables. No usar clases
  con setters para DTOs de capa REST.

- Los **servicios de aplicación** (`application/service/`) orquestan; la lógica
  de negocio pertenece al dominio. Los repositorios solo se inyectan en servicios
  de aplicación, nunca en controladores.

- Los **controladores** no llaman a repositorios directamente.

- Las excepciones de dominio (`domain/exception/`) son `RuntimeException`s
  concretas; los `@RestControllerAdvice` de cada módulo las mapean a
  `ProblemDetail`.

---

## Convenciones de tests

### Pirámide de tests

| Capa | Anotación principal | Usa contenedores |
|---|---|---|
| Unitario | `@ExtendWith(MockitoExtension.class)` | No |
| Slice MVC | `@WebMvcTest` | No |
| Integración user module | `UserModuleIntegrationTest` | Postgres + Redis |
| Integración general | `InfraIntegrationTest` | Postgres + Redis |

### Clase base para tests de sesiones

```java
class MiSessionTest extends UserModuleIntegrationTest {

    @Test
    void ejemplo() throws Exception {
        Tokens tokens = registerAndLogin(); // registra usuario fresco + login

        mockMvc.perform(get("/api/v1/me/sessions")
                .header("Authorization", "Bearer " + tokens.accessToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].current").value(true));
    }
}
```

`UserModuleIntegrationTest` está en:
`src/test/java/com/mx/cryptomonitor/integration/user/support/`

### Propiedades de test

- `application-test.properties` → configuración base (H2, JWT dummy, OAuth2 dummy).
- Tests que necesiten Postgres real o Redis real sobreescriben con
  `@DynamicPropertySource` (ver `TokenControllerIT` como referencia).
- El secreto JWT de test es siempre:
  `MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=`
- Nunca usar `@Disabled` para saltar tests que fallan; arreglar la causa raíz.

### Contenedores Testcontainers

```java
// Imágenes fijadas — no usar "latest"
postgres:16-alpine
redis:7
```

Usar `@ServiceConnection` para que Spring Boot conecte automáticamente
el `DataSource` y el `RedisConnectionFactory` al puerto asignado por Docker.

---

## Código Java — estilo

- Formatear con `./mvnw spotless:apply` antes de cada commit
  (Google Java Format, estilo GOOGLE).
- Sin comentarios que expliquen *qué* hace el código; solo el *por qué*
  cuando no es obvio.
- No añadir manejo de errores para escenarios imposibles; confiar en las
  garantías del framework.
- Los campos `@Autowired` solo se usan en tests y configuración de seguridad
  (`@Lazy`). En el resto, inyección por constructor vía `@RequiredArgsConstructor`.
