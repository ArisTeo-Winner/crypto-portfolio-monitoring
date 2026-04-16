# Ajuste aplicado: error de Surefire al cerrar fork JVM

## Contexto

Se presentaba en consola:

`[ERROR] Surefire is going to kill self fork JVM. The exit has elapsed 30 seconds after System.exit(0).`

## Causa identificada

1. En pruebas de integración con Testcontainers se usaba `ddl-auto=create-drop`, lo que provocaba intentos de DDL al cierre cuando el contenedor de PostgreSQL ya no estaba disponible.
2. Varias pruebas usaban `@SpringBootTest(webEnvironment = RANDOM_PORT)` sin necesitar servidor embebido real, aumentando hilos/recursos activos al apagar el contexto.

## Ajustes realizados

1. Se cambió `ddl-auto` de `create-drop` a `create` en:
   - `src/test/resources/application-test.properties`
   - `src/test/java/com/mx/cryptomonitor/integration/AuthLoginIntegrationTest.java`
   - `src/test/java/com/mx/cryptomonitor/integration/AuthRefreshTokenRotationIT.java`
   - `src/test/java/com/mx/cryptomonitor/integration/infrastructure/security/oauth2/GoogleAuthorizationCodeOidcIT.java`
   - `src/test/java/com/mx/cryptomonitor/integration/user/infrastructure/inbound/rest/TokenControllerIT.java`

2. Se cambió `RANDOM_PORT` a `MOCK` en pruebas que usan `MockMvc`:
   - `src/test/java/com/mx/cryptomonitor/controller/UserControllerIntegrationTest.java`
   - `src/test/java/com/mx/cryptomonitor/integration/PortfolioControllerIntegrationTest.java`
   - `src/test/java/com/mx/cryptomonitor/integration/controller/MarketDataControllerIntegrationTest.java`
   - `src/test/java/com/mx/cryptomonitor/integration/UserControllerIT.java`

3. Se agregó tolerancia de cierre en Surefire:
   - `pom.xml`:
     - `forkedProcessExitTimeoutInSeconds=120`

## Validación

Comando ejecutado:

```powershell
.\mvnw -q clean install
```

Resultado:

- Build exit code `0`.
- No se volvió a generar el mensaje de error de Surefire.
- No se generaron archivos `*.dump`/`*.dumpstream` en `target/surefire-reports` para este fallo.

