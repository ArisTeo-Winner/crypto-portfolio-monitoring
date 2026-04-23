# Crypto Portfolio Monitoring Backend

Backend API for a multi-asset portfolio tracker focused on secure authentication,
transaction processing, portfolio accounting, and market data integration.

## Backend Overview

This backend is implemented as a modular monolith with Spring Boot and clear
module boundaries for:

- authentication and user security
- transaction lifecycle management
- portfolio projection and PnL accounting
- asset catalog and lookup
- market data provider integrations
- shared infrastructure concerns such as caching, validation, and auditing

The backend exposes REST APIs consumed by a separate Next.js frontend.

## Key Features

### Authentication and Access Control

- Email/password login with JWT access tokens
- Refresh-token flow backed by Redis
- OAuth2 login support
- Role-based protection for authenticated endpoints
- Security audit logging for sensitive actions

### Portfolio Management

- Multi-asset portfolio support:
  - `CRYPTO`
  - `STOCK`
  - `ETF`
  - `INDEX`
- Portfolio views by asset type and by individual asset
- Rebuilt portfolio projections from transaction history
- Holdings performance timeline for portfolio and asset detail views
- Current value, cost basis, and all-time profit calculations

### Transaction Engine

- Create transactions:
  - `BUY`
  - `SELL`
  - `TRANSFER`
- Edit existing transactions
- Delete existing transactions
- Automatic portfolio reconciliation after transaction updates and deletes
- Stable sorting of transaction history from most recent to oldest

### Market Data Integration

- Crypto spot price retrieval
- Stock price retrieval
- Provider fallback strategy for quote resolution
- Historical crypto price integration through CoinGecko
- Cached historical price retrieval for charting and performance analytics

### Security and Compliance

- Secure HTTP response headers for the API
- Redaction of sensitive request data in logs
- Audit logging isolated behind ports/adapters
- SonarQube, JaCoCo, ArchUnit, and Spring Modulith verification

## Technical Stack

### Core Technologies

- `Java 21`
- `Spring Boot 3.5.x`
- `Spring Security`
- `Spring Data JPA`
- `Hibernate`
- `PostgreSQL`
- `Redis`
- `Spring Web`
- `Spring WebFlux / WebClient`
- `Spring Modulith`

### Key Libraries

- `jjwt` for JWT token handling
- `MapStruct` for DTO/entity mapping
- `Lombok` for boilerplate reduction
- `springdoc-openapi` for Swagger/OpenAPI documentation
- `spring-dotenv` for environment loading
- `Spring Cache` for provider and market data caching

### Testing and Quality Tooling

- `JUnit 5`
- `Mockito`
- `Spring Boot Test`
- `Spring Security Test`
- `ArchUnit`
- `Spring Modulith Test`
- `Testcontainers`
- `H2`
- `WireMock`
- `MockWebServer`
- `JaCoCo`
- `SonarQube`
- `Spotless`
- `Checkstyle`

## Performance and Architecture

### Architecture Style

The backend follows a modular monolith architecture with strong influence from
hexagonal architecture and ports-and-adapters design.

Primary modules:

- `asset`
- `marketdata`
- `portfolio`
- `transaction`
- `user`
- `shared`

### Hexagonal Design Principles

- Controllers live in `infrastructure.inbound`
- External provider integrations live in `infrastructure.outbound`
- Application use cases are defined through `application.port.in`
- Cross-module and provider dependencies are exposed through `application.port.out`
- Business logic is concentrated in application/domain services instead of controllers

### Performance and Reliability Characteristics

- Redis-backed caching for frequently requested values
- Cached historical crypto series for chart endpoints
- Provider fallback handling for upstream quote failures
- Portfolio projections rebuilt from transaction history to recover from drift
- Reduced write amplification by avoiding unnecessary projection writes
- Retry and fallback behavior around optimistic locking during projection reconciliation

### Data Consistency Model

- `transaction` is the source of truth for portfolio history
- `portfolio_entry` is a derived projection
- The system reconciles projections after edit/delete operations
- Read paths can self-heal stale portfolio projections when historical data changes

## Security Posture

The API currently includes:

- `Content-Security-Policy`
- `X-Frame-Options`
- `X-Content-Type-Options`
- `Referrer-Policy`
- `Permissions-Policy`
- `Strict-Transport-Security` on secure requests

Authentication-sensitive DTOs redact secrets in logs, and audit logging is
isolated behind an outbound port to preserve architectural boundaries.

## Build and Run

### Compile

```powershell
.\mvnw clean compile
```

### Run Jar Locally

The local `java -jar` process runs from Windows, so database and Redis hosts must use
`localhost`. Docker Compose uses the internal service name `java_db` automatically.

If Docker backend is already exposing port `8080`, set `SERVER_PORT=8081` in `.env`
or stop the backend container before running the jar.

```powershell
java -jar target\crypto-portfolio-monitoring-0.0.1-SNAPSHOT.jar
```

Then verify:

```powershell
Invoke-RestMethod http://localhost:8081/api/v1/health
```

### Run tests

```powershell
.\mvnw test
```

### Full verification

```powershell
.\mvnw clean verify
```

## API Documentation

Swagger/OpenAPI is provided through Springdoc. Once the backend is running,
check the configured Swagger UI endpoint in your local environment.

## Health Checks

Public API health endpoint:

```text
GET /api/v1/health
```

Operational Actuator probes:

```text
GET /actuator/health
GET /actuator/health/liveness
GET /actuator/health/readiness
```

## Production Operations

Production-shaped PostgreSQL and Redis configuration is documented in
`docs/operations/POSTGRESQL_PRODUCTION_READINESS.md`.
The concrete go/no-go database checklist is available in
`docs/operations/DATABASE_PRODUCTION_CHECKLIST.md`.

For staging or production-style validation:

```powershell
.\mvnw -B -DskipTests package
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

The production compose keeps PostgreSQL and Redis off host ports, requires a
Redis password, uses the `prod` profile, and keeps Flyway strict by default.

