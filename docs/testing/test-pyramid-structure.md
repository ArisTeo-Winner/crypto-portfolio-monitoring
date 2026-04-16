# Test Pyramid Structure (Mirror of src/main)

Goal: keep `src/test` a strict mirror of `src/main`, but separated by test level (test pyramid).

## Directories / Packages

- Unit tests (no Spring context)
  - Root: `src/test/java/com/mx/cryptomonitor/unit/`
  - Package prefix: `com.mx.cryptomonitor.unit.`
  - Mirror rule: after `.unit.` the package path mirrors `src/main/java/com/mx/cryptomonitor/**`.
  - Examples:
    - `com.mx.cryptomonitor.unit.infrastructure.security.LoginRateLimiterUnitTest`
    - `com.mx.cryptomonitor.unit.user.infrastructure.inbound.rest.security.PasswordResetRateLimiterUnitTest`

- Slice tests (Spring MVC slice, `@WebMvcTest`)
  - Root: `src/test/java/com/mx/cryptomonitor/slice/webmvc/`
  - Package prefix: `com.mx.cryptomonitor.slice.webmvc.`
  - Mirror rule: after `.slice.webmvc.` the package path mirrors the controller package under `src/main`.
  - Examples:
    - `com.mx.cryptomonitor.slice.webmvc.user.infrastructure.inbound.rest.UserControllerOwaspWebMvcTest`
    - `com.mx.cryptomonitor.slice.webmvc.auth.infrastructure.inbound.rest.AuthControllerLoginWebMvcTest`
    - `com.mx.cryptomonitor.slice.webmvc.auth.infrastructure.inbound.rest.AuthControllerOwaspWebMvcTest`

- Integration tests (Spring Boot context, optionally Testcontainers/WireMock)
  - Root: `src/test/java/com/mx/cryptomonitor/integration/`
  - Package prefix: `com.mx.cryptomonitor.integration.`
  - Mirror rule: after `.integration.` the package path mirrors the integration target in `src/main`.
  - Example:
    - `com.mx.cryptomonitor.integration.user.infrastructure.inbound.rest.UserControllerAccessControlSecurityIT`

## Naming

- Unit: `*Test` or `*UnitTest` (keep deterministic, no sleeps).
- Slice: `*WebMvcTest`.
- Integration: `*IT` (or `*IntegrationTest` if consistent with your build rules).

## Practical Rules

- Tests must not require unrelated beans to start.
  - For integration tests, prefer a minimal `@SpringBootTest(classes=...)` config that imports only the module under test.
- Prefer deterministic clocks in rate limiters.
  - If a class needs a deterministic clock, expose a constructor explicitly marked "Visible for testing".

## Execution (examples)

```powershell
.\mvnw "-Dtest=*WebMvcTest" test
.\mvnw "-Dtest=*RateLimiter*Test" test
.\mvnw "-Dtest=*IT" test
```
