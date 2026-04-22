You are a senior DevSecOps engineer performing a security audit on a production-grade system.

System context:
- Backend: Java 21 + Spring Boot 4 (JWT + OAuth2/OIDC Google)
- Architecture: Portfolio Tracker (no custody of funds)
- Infra: Docker + Jenkins + PostgreSQL + Redis
- Cloud: Google Cloud / Azure
- External APIs: CoinMarketCap, AlphaVantage, Polygon
- Auth: JWT (access + refresh) + OAuth2 login (Google)
- CI/CD: Jenkins pipelines

Your goal:
Evaluate the system against AppSec + CloudSec risks similar to supply chain escalation incidents (e.g. OAuth compromise → secrets exposure → CI/CD compromise).

Follow OWASP best practices:
- Secrets must never be exposed in logs, code, or pipelines :contentReference[oaicite:1]{index=1}
- Secrets must be centrally managed and access-controlled :contentReference[oaicite:2]{index=2}
- CI/CD must enforce least privilege and identity isolation
- OAuth integrations must be restricted and audited

---

## TASK

For each control below:
1. Determine if it is implemented (YES / NO / PARTIAL)
2. Explain risk if missing
3. Provide concrete fix (code/config/infra)
4. Assign severity (CRITICAL / HIGH / MEDIUM / LOW)

---

## 🔴 SECTION 1 — Identity & OAuth Security

- Separate accounts (personal vs dev vs cloud)
- MFA enforced (GitHub, Cloud, CI/CD)
- OAuth third-party apps restricted
- OAuth apps reviewed and revoked periodically
- No admin privilege derived from OAuth email alone

---

## 🔴 SECTION 2 — Secrets Management

- Secrets NOT stored in:
  - .env (production)
  - Docker images
  - Git repository
  - logs
- Secrets stored in:
  - Secret Manager (GCP / Azure)
- Secrets are:
  - encrypted at rest
  - rotated periodically
  - scoped per service
- No secrets exposed via environment inspection or debug endpoints

---

## 🔴 SECTION 3 — Secret Segmentation (Blast Radius)

- Secrets separated by:
  - domain (auth / infra / marketdata / ci)
  - environment (dev / qa / prod)
- Least privilege IAM per secret
- No shared global secrets

---

## 🔴 SECTION 4 — CI/CD Security (Jenkins)

- No secrets hardcoded in pipelines
- Secrets injected securely (vault / credential store)
- Build agents are ephemeral
- GitHub tokens have minimal scope
- Dependency versions are pinned (no latest)
- Pipeline permissions restricted
- No unauthorized pipeline modification possible

---

## 🔴 SECTION 5 — Cloud IAM & Identity

- No static service account keys
- Uses:
  - Workload Identity (GCP) or Managed Identity (Azure)
- IAM roles follow least privilege
- Secret access is audited and logged

---

## 🟠 SECTION 6 — Application Runtime Security

- Environment variables not exposed in logs/UI
- Sensitive values masked
- JWT validation includes:
  - signature
  - expiration
  - session validation
- No user enumeration (login returns generic errors)

---

## 🟠 SECTION 7 — External Integrations

- OAuth used instead of API keys when possible
- API keys:
  - read-only
  - encrypted at rest
- No request for:
  - private keys
  - seed phrases
  - credentials of external platforms

---

## 🟠 SECTION 8 — Monitoring & Detection

- Alerts for:
  - abnormal secret access
  - login anomalies
  - pipeline changes
- Logging enabled for:
  - authentication events
  - secret access
- Metrics integrated (Prometheus/Grafana)

---

## 🟡 SECTION 9 — Incident Response

- Secret rotation strategy exists
- OAuth revocation process defined
- Ability to:
  - invalidate JWT sessions
  - disable pipelines
- Incident playbook documented

---

## 🟡 SECTION 10 — UX Security (Anti-Phishing)

- UI warns users:
  - never share private keys
  - never enter seed phrases
- Detect suspicious inputs (e.g. 12/24 word seeds)
- Explicit confirmation before sensitive input

---

## OUTPUT FORMAT

Return a structured report:

[
  {
    "control": "...",
    "status": "YES | NO | PARTIAL",
    "risk": "...",
    "fix": "...",
    "severity": "CRITICAL | HIGH | MEDIUM | LOW"
  }
]

Also include:
- Top 5 critical risks
- Recommended remediation order
- Security maturity level (1–5)

---

## IMPORTANT

Do NOT give generic advice.
Tie every recommendation to:
- this architecture
- real attack paths (OAuth compromise → secrets → CI/CD → cloud)

Focus on:
- preventing lateral movement
- reducing blast radius
- eliminating secret exposure