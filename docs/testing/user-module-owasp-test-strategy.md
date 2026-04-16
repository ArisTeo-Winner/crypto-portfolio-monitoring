# User Module Testing Strategy (OWASP API Top 10)

## 1) Brutal Gap Assessment (Current Codebase)

- Endpoint inventory mismatch:
  - Target contract requested: `/api/v1/users/me`, `/password/reset`, `/password/change`, `/email/verify`.
  - Current implementation: `/api/v1/users/register`, `/api/v1/auth/login`, `/api/v1/users/{email}`, `/api/v1/users/profile`, `/api/v1/users/{id}` and `/api/v1/auth/*`.
- Security contract inconsistencies:
  - `DELETE /api/v1/users/{id}` and `PUT /api/v1/users/profile` return plain text/null, not RFC 9457 Problem Details.
  - `PUT /api/v1/users/profile` returns `User` entity and can expose `passwordHash`.
  - `AuthService#login` logs raw password in `INFO` message.
  - `AuthController` invalid refresh token path currently falls into generic `500 INTERNAL_ERROR`, not `401`.
- Security config risks:
  - `csrf().disable()` globally.
  - `StrictHttpFirewall` allows encoded slash/backslash/semicolon/encoded percent.
  - Public path list mismatch (`/api/v1/users/public/test` vs implemented `/api/v1/users/public/test-get`).

## 2) Automated Coverage Added In This Iteration

- WebMvc (slice) `src/test/java/com/mx/cryptomonitor/slice/webmvc/**`:
  - `UserControllerOwaspWebMvcTest` (package `com.mx.cryptomonitor.slice.webmvc.user.infrastructure.inbound.rest`):
    - register: `201/400/429`, SQLi/XSS payload rejection, RFC 9457 schema checks, `Retry-After`.
    - get/delete/profile: `200/404/500/400`, sensitive-data checks and known-gap validation for `passwordHash` exposure.
  - `AuthControllerLoginWebMvcTest` (package `com.mx.cryptomonitor.slice.webmvc.auth.infrastructure.inbound.rest`):
    - login: `200/401/429/500`, secure cookie checks (`HttpOnly`, `Secure`, `Path`, `Max-Age`), generic auth errors, `Retry-After`.
  - `AuthControllerOwaspWebMvcTest` (package `com.mx.cryptomonitor.slice.webmvc.auth.infrastructure.inbound.rest`):
    - login: OWASP-focused assertions (RFC 9457/Problem Details shape, `traceId`, retry headers, no credential echo).
  - `UserControllerRateLimitAllEndpointsWebMvcTest`:
    - rate limit coverage for all user endpoints (429 + `Retry-After` where applicable).
  - `UserControllerValidationWebMvcTest`:
    - request validation (`400`) + controlled `500` (no sensitive detail leaks).
- Unit `src/test/java/com/mx/cryptomonitor/unit/**`:
  - Deterministic (clock-driven) rate limiter unit tests:
    - register/login/password reset/password change/email verify/me read/me write/me delete.
- Security integration `src/test/java/com/mx/cryptomonitor/integration/**`:
  - `UserControllerAccessControlSecurityIT` (package `com.mx.cryptomonitor.integration.user.infrastructure.inbound.rest`):
    - `GET /api/v1/users`: `401` unauthenticated, `403` role USER, `200` role ADMIN.
- Testcontainers integration:
  - `AuthRefreshTokenRotationIT`:
    - login + refresh rotation.
    - replay old refresh token denied (currently `500` as known gap).
    - logout revokes refresh token and closes session.

### 2.1) OAuth2 (Google) Authorization Code + OIDC Coverage

Important clarification about the real flow:
- The OAuth2 Authorization Code callback handled by Spring Security is **`GET /login/oauth2/code/google`**.
- `src/main/java/com/mx/cryptomonitor/user/infrastructure/inbound/rest/OAuth2AuthController.java` exposes **`GET /api/v1/oauth2/callback`** (`handleGoogleLogin`), but **it is not wired into the OAuth2 login flow** and currently returns empty tokens (`new JwtResponse("", "")`). Treat it as a **known-gap / dead endpoint surface** until it is either removed or properly integrated.

Tests added/updated:
- Slice WebMVC:
  - `src/test/java/com/mx/cryptomonitor/slice/webmvc/application/controllers/OAuth2AliasControllerWebMvcTest.java`
    - `GET /api/v1/oauth2/authorize/google` redirects to `/oauth2/authorization/google`.
- Integration (WireMock + Testcontainers Postgres + MockMvc):
  - `src/test/java/com/mx/cryptomonitor/integration/infrastructure/security/oauth2/GoogleAuthorizationCodeOidcIT.java`
    - Happy path: `/oauth2/authorization/google` -> `/login/oauth2/code/google` -> internal JWT (access+refresh) -> `/api/v1/users/me` works.
    - OWASP auth hardening: state tampering, callback without session (must not exchange code), issuer/audience mismatch, expired ID token, invalid signature.
    - Broken access control: OAuth2-created user must not access `GET /api/v1/users` (expects `403`).

## 3) JMeter Snippets (API Performance + Rate Limit)

```bash
jmeter -n \
  -t ci/security/jmeter/user-auth-rate-limit.jmx \
  -JbaseUrl=http://localhost:8080 \
  -Jusers=40 \
  -l target/jmeter/user-auth-rate-limit.jtl
```

Minimal scenario design:
- Thread Group A: `POST /api/v1/auth/login` burst.
- Assertion: expect `429` after threshold and `Retry-After` header.
- Thread Group B: `POST /api/v1/users/register` with unique emails.
- Assertion: p95 latency and proper `201/429` distribution.

## 4) ZAP / Burp / ffuf Execution

```bash
bash ci/security/zap-baseline-user.sh http://localhost:8080
```

```bash
bash ci/security/ffuf-login-bruteforce.sh http://localhost:8080
```

Burp payload sets to prioritize:
- SQLi: `' OR '1'='1`, `admin'--`, `UNION SELECT 1,2,3`.
- XSS: `<script>alert(1)</script>`, `"><svg/onload=alert(1)>`.
- Auth abuse: credential stuffing lists + token replay attempts.

## 5) CI/CD Recommendation

- Required quality gates in PR:
  - `mvn -Dtest=*User*WebMvcTest,*Auth*WebMvcTest test`
  - `mvn -Dtest=UserControllerAccessControlSecurityIT,AuthRefreshTokenRotationIT test`
  - JaCoCo threshold >95% in user/auth package.
  - ZAP baseline report with fail on high risk alerts.
  - ffuf brute-force smoke with expected `429` behavior.

## 6) Change Log (What Was Adjusted)

- Added OAuth2/OIDC tests (no real Google calls):
  - `src/test/java/com/mx/cryptomonitor/integration/infrastructure/security/oauth2/GoogleAuthorizationCodeOidcIT.java`
  - `src/test/java/com/mx/cryptomonitor/slice/webmvc/application/controllers/OAuth2AliasControllerWebMvcTest.java`
- Hardened OAuth2 IT with negative security cases (iss/aud/exp/signature/state/session) + access control assertion (`/api/v1/users` forbidden for USER).
