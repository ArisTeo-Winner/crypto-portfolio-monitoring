flowchart TD

    A[SDLC: Planning] --> B[Design]
    B --> C[Development]
    C --> D[Testing]
    D --> E[Deployment]
    E --> F[Maintenance]

    A --> A1[Requisitos<br/>Riesgos<br/>Threat Modeling<br/>Historias BDD]
    B --> B1[Arquitectura<br/>API-First<br/>Modelo modular<br/>Spring Modulith<br/>ADR]
    C --> C1[Code Quality<br/>Spotless<br/>Checkstyle<br/>ArchUnit<br/>Unit Tests]
    D --> D1[Test Pyramid<br/>Unitarias<br/>Integración<br/>WireMock<br/>Testcontainers<br/>E2E<br/>Mutation Testing]
    E --> E1[Release Pipeline<br/>JaCoCo<br/>SonarQube<br/>OWASP ZAP<br/>RESTler<br/>Docker<br/>Deploy]
    F --> F1[Operate & Monitor<br/>Logs<br/>Metrics<br/>Tracing<br/>Redis Observability<br/>Maintenance]