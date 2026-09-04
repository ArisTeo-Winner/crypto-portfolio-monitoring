# README-SEGURIDAD — Arquitectura DevSecOps SCA

Software Composition Analysis (SCA) en 3 capas complementarias para
`crypto-portfolio-monitoring` (Spring Boot 3.5.11 / Java 21).

| Capa | Herramienta | Qué escanea | Cuándo | Gate |
|---|---|---|---|---|
| 0 | **GitHub Dependabot** | Dependencias Maven, imágenes Docker, GitHub Actions | Continuo (en el repo) | PRs de actualización + alertas |
| 1 | **OWASP Dependency-Check** (Maven) | Dependencias (CVEs vía NVD) | `mvn verify` / pipeline | Falla si **CVSS ≥ 7** |
| 2 | **Trivy** | Imagen Docker: OS Alpine, libs, secretos, misconfig | Post-build en el pipeline | Falla si **HIGH/CRITICAL** |
| 3 | **OWASP Dependency-Track** | Monitoreo continuo del SBOM (CycloneDX) | Continuo (dashboard) | Alertas de nuevos CVEs |

**Cómo encajan las capas:** Dependabot **previene** (mantiene las versiones al día
antes de que el CVE llegue al build); Dependency-Check **bloquea** en el build;
Trivy cubre la **capa del contenedor** (que Dependabot y Dependency-Check no ven);
Dependency-Track da **visibilidad continua** del SBOM ya desplegado.

---

## ⚠️ Desviaciones deliberadas respecto al prompt original

Tres decisiones tomadas por seguridad/no-destructividad. Si prefieres el
comportamiento literal del prompt, pídemelo y lo ajusto.

1. **No se añadieron dependencias vulnerables falsas** (`log4j-core 2.14.1`,
   `spring-security 5.x`) al `pom.xml`. Esto es una app real con autenticación
   JWT y sesiones; reintroducir Log4Shell sería un riesgo real. **No hace falta**:
   el proyecto ya contiene CVEs reales para demostrar el escaneo (ver
   [Casos de prueba](#-casos-de-prueba)).
2. **No se sobrescribió tu `Jenkinsfile` ni tu `dockerfile`** (ambos funcionan y
   son no triviales). Se entregan como archivos nuevos:
   `Jenkinsfile.devsecops` y `Dockerfile.secure`.
3. **Dependency-Track corre en un compose aparte** (`docker-compose.dependency-track.yml`)
   en los puertos host **8081/8082** para no chocar con la app en 8080.

---

## Archivos entregados

```
.github/workflows/security-fast.yml    # CI SCA — carril rápido (cada push/PR): SBOM + Trivy
.github/workflows/security-deep.yml    # CI SCA — carril profundo (nightly/manual): Dep-Check + DT
.github/dependabot.yml                 # Capa 0: PRs de actualización (maven/docker/actions)
pom.xml                                # + plugins dependency-check y cyclonedx
dependency-check-suppressions.xml      # supresiones (falsos positivos)
Dockerfile.secure                      # imagen endurecida (no-root, healthcheck)
.dockerignore                          # contexto de build minimo
Jenkinsfile.devsecops                  # pipeline SCA alternativo (Jenkins/agente Linux) — portfolio
docker-compose.dependency-track.yml    # stack Dependency-Track (apiserver+frontend+pg)
scripts/upload-sbom.sh                 # subida del SBOM al Dependency-Track
README-SEGURIDAD.md                    # este archivo
```

---

## Prerrequisitos

- **NVD API Key** (recomendada) para Dependency-Check — evita rate-limit:
  solicítala gratis en https://nvd.nist.gov/developers/request-an-api-key
- **Docker** con ≥ 4 GB de RAM asignados (Dependency-Track apiserver lo exige).
- **Trivy**: no requiere instalación local si se usa vía Docker (`aquasec/trivy`).

---

## Capa 0 — GitHub Dependabot (`.github/dependabot.yml`)

Dependabot es **gratuito** en GitHub y actúa antes que todo lo demás: mantiene
las dependencias actualizadas para que el CVE nunca llegue a romper el build.

Cubre 3 ecosistemas de este repo:
- **`maven`** → `pom.xml` (minor/patch agrupados en un PR; major sueltos).
- **`docker`** → `dockerfile` y `Dockerfile.secure` (actualiza imágenes base).
- **`github-actions`** → `.github/workflows/*` (fija/actualiza las actions).

### Activación (2 pasos, uno es en la UI de GitHub)
1. El archivo `.github/dependabot.yml` habilita las **version updates** (los PRs
   semanales). Con hacer merge del archivo a `master` basta.
2. En **Settings → Advanced Security** del repo, activa:
   - **Dependabot alerts** — avisos de CVEs en tus dependencias.
   - **Dependabot security updates** — PRs automáticos que corrigen esos CVEs.

   Estas dos son ajustes del repositorio y **no** se pueden fijar por archivo.

### Cómo se relaciona con las otras capas
- Dependabot **abre el PR de fix**; tu pipeline (Dependency-Check + Trivy) lo
  **valida** antes de mergear. Es el ciclo completo: detectar → parchear → verificar.
- Si Dependency-Check te bloquea el build por un CVE, muchas veces ya habrá un
  PR de Dependabot con la versión corregida esperando.

---

## Capa 1 — OWASP Dependency-Check (local)

El plugin está ligado a la fase `verify`, así que **no** corre en `mvn test` ni
`mvn package`. Se dispara solo con `mvn verify` o con el goal directo.

```bash
# Escaneo completo (rompe el build si CVSS >= 7). La 1a vez descarga la NVD (lento).
mvn verify -DskipTests -Dnvd.api.key=TU_API_KEY

# Solo el goal, sin el resto de verify:
mvn org.owasp:dependency-check-maven:check -Dnvd.api.key=TU_API_KEY

# Saltar el escaneo (interruptor de emergencia):
mvn verify -Ddependency-check.skip=true

# Cambiar el umbral (ej. solo fallar en CRITICAL >= 9):
mvn verify -Dsca.failBuildOnCVSS=9 -Dnvd.api.key=TU_API_KEY
```

**Reportes** (en `target/`): `dependency-check-report.html` (legible),
`.xml` (para el plugin de Jenkins), `.json` (para automatización).

**Caché compartida**: la base NVD se guarda en
`~/.owasp/dependency-check-data` (propiedad `odc.data.directory`) y se reutiliza
entre builds. Para compartirla en CI, cachea ese directorio.

### Interpretar el reporte HTML
- Cada dependencia lista sus CVEs con **score CVSS** y vector.
- Prioriza por CVSS y por si hay versión fija disponible (`fixed`).
- **Remediar > suprimir**: la primera opción siempre es subir la versión.

### Gestionar falsos positivos → `dependency-check-suppressions.xml`
1. Verifica que el CVE realmente **no** aplica (o que el riesgo se acepta).
2. Añade un `<suppress>` acotado por `packageUrl`/`sha1`, **con `<notes>`** que
   expliquen quién lo aprobó y por qué (y `until="fecha"` si es temporal).
3. Revisa el archivo en cada release; borra las supresiones ya innecesarias.

---

## Capa 2 — Trivy (imagen Docker, local)

```bash
# 1) Construir la imagen endurecida
docker build -f Dockerfile.secure -t crypto-portfolio-monitoring:local .

# 2) Escaneo con reporte HTML (no rompe)
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$PWD":/workspace aquasec/trivy:latest image \
  --scanners vuln,secret,config --no-progress \
  --format template --template "@/contrib/html.tpl" \
  --output /workspace/trivy-report.html \
  crypto-portfolio-monitoring:local

# 3) Gate (rompe con HIGH/CRITICAL corregibles)
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  aquasec/trivy:latest image \
  --scanners vuln,secret,config --severity HIGH,CRITICAL \
  --ignore-unfixed --exit-code 1 crypto-portfolio-monitoring:local
```

En Windows PowerShell, reemplaza `"$PWD"` por `"${PWD}"` y `/var/run/docker.sock`
funciona igual con Docker Desktop.

`--ignore-unfixed` evita fallar por CVEs de Alpine que aún no tienen parche
disponible; quítalo si quieres política de tolerancia cero.

---

## Capa 3 — Dependency-Track (monitoreo continuo)

### Levantar el stack
```bash
# 1) Definir la contraseña de la BD del dashboard
echo "DTRACK_DB_PASSWORD=$(openssl rand -hex 24)" > .env.dtrack

# 2) Arrancar (apiserver tarda 1–2 min en migrar la BD la 1a vez)
docker compose -f docker-compose.dependency-track.yml --env-file .env.dtrack up -d

# 3) Ver estado
docker compose -f docker-compose.dependency-track.yml ps
```

**URLs locales**:
- Frontend (dashboard): http://localhost:8082
- API server: http://localhost:8081

### Primer acceso y API Key
1. Entra al frontend con **`admin` / `admin`** y **cambia la contraseña** de inmediato.
2. `Administration → Access Management → Teams` → equipo *Automation* (o crea uno)
   → **Create API Key**. Asegura los permisos `BOM_UPLOAD`, `PROJECT_CREATION_UPLOAD`,
   `VIEW_PORTFOLIO`.
3. (Opcional) Crea el proyecto manualmente en `Projects → Create Project`
   (nombre `crypto-portfolio-monitoring`) y copia su **UUID**. Si no lo creas, el
   script lo auto-crea por nombre+versión.

### Subir el SBOM
```bash
# Generar el SBOM (CycloneDX JSON) -> target/bom.json
mvn -DskipTests package        # o: mvn -DskipTests cyclonedx:makeAggregateBom

# Subirlo (modo auto-crear por nombre/versión)
DTRACK_API_KEY=odt_xxx ./scripts/upload-sbom.sh

# O contra un proyecto existente por UUID
DTRACK_API_KEY=odt_xxx DTRACK_PROJECT_UUID=<uuid> ./scripts/upload-sbom.sh
```

Una vez subido, Dependency-Track **re-evalúa el SBOM contra nuevos CVEs sin
necesidad de re-escanear**, y puede enviar alertas (`Administration → Notifications`).

---

## 🧪 Casos de prueba

| # | Caso | Cómo demostrarlo |
|---|---|---|
| 1 | Build falla por CVSS ≥ 7 | `mvn verify` — el proyecto ya trae CVEs reales, p.ej. `org.json:20230227` (CVE-2023-5072, CVSS 7.5) y `postgresql:42.6.0` (CVE-2024-1597). |
| 2 | Trivy detecta vuln en Alpine | Escanea la imagen; los paquetes OS antiguos aparecen como HIGH/CRITICAL. |
| 3 | SBOM CycloneDX JSON | ✅ Verificado: `target/bom.json`, `bomFormat=CycloneDX`, `specVersion=1.5`, 199 componentes. |
| 4 | Upload a Dependency-Track | `./scripts/upload-sbom.sh` → HTTP 200 y proyecto visible en el dashboard. |
| 5 | Supresión funciona | Añade el CVE del caso 1 al `dependency-check-suppressions.xml` y verifica que el build ya no falla por él. |

> Para el caso 1, la remediación real es actualizar `org.json` a `20240303+` y
> `postgresql` a `42.7.x+`. Usa la supresión solo para la demo del caso 5.

---

## Ejecución del SCA — GitHub Actions (recomendado)

Con el stack ya gestionado (**código en GitHub, app en Render, Redis en Upstash**),
la vía natural para el CI es **GitHub Actions**: runners Linux con `sh` y Docker de
fábrica, nativos al repo y **sin infra propia que mantener**. Los dos workflows
replican el **modelo de dos velocidades**:

| Workflow | Carril | Dispara | Contenido |
|---|---|---|---|
| `.github/workflows/security-fast.yml` | **Rápido** | cada `push` / `pull_request` | Build + SBOM (CycloneDX) → Docker → **gate Trivy** (HIGH/CRITICAL) |
| `.github/workflows/security-deep.yml` | **Profundo** | `cron` nocturno + `workflow_dispatch` | **Dependency-Check** (NVD, con caché) + subida a **Dependency-Track** (best-effort) |

### Secrets a configurar (`Settings → Secrets and variables → Actions`)
- `NVD_API_KEY` — clave NVD para Dependency-Check (sin ella corre, pero lento).
- `DTRACK_URL` + `DTRACK_API_KEY` — **solo** si Dependency-Track es alcanzable
  públicamente (ver caveat abajo).

### Caché de la NVD en runners efímeros
El carril profundo usa `actions/cache` con clave rodante (`nvd-data-*` +
`restore-keys`), así la base NVD se **restaura y actualiza incrementalmente** en
vez de re-descargarse entera cada noche. Es el equivalente al `dataDirectory`
persistente de Jenkins, pero para runners que nacen vacíos.

### ⚠️ Dependency-Track desde runners hosteados
Los runners de GitHub son **efímeros en la nube** y **no alcanzan un
`localhost:8081`** de tu máquina. Por eso el paso de subida está **guardado**: si
no defines `DTRACK_URL`/`DTRACK_API_KEY`, **se omite** y el SBOM queda archivado
como artefacto del workflow. Para usar DT de verdad desde Actions, **hostéalo
público** (p. ej. en Render, coherente con tu stack) o usa un self-hosted runner.

---

## Pipeline Jenkins (`Jenkinsfile.devsecops`) — alternativa / portfolio

> **Nota:** con el stack ya gestionado (GitHub + Render + Upstash), la vía
> **recomendada es GitHub Actions** (arriba). Este pipeline Jenkins se conserva
> como **alternativa / demostración de portfolio**: es funcionalmente equivalente a
> los workflows, pero requiere que **tú** alojes el agente Linux+Docker (WSL2,
> contenedor o VM), ya que tu Jenkins corre nativo en Windows.

Pipeline para **agente Linux con Docker** (tu `Jenkinsfile`
principal es Windows/bat y orquesta compose; este es complementario). Está
dividido en **dos velocidades** para no pagar el costo del SCA pesado en cada
push:

| Velocidad | Etapas | Cuándo corre | Gate |
|---|---|---|---|
| **Ligero (por commit)** | Checkout → Build & SBOM → Docker Build → **Trivy** | En **cada** build | Trivy **rompe** el build ante HIGH/CRITICAL (`--ignore-unfixed`) |
| **Deep (nocturno / on-demand)** | **OWASP Dependency-Check** (NVD) → **Upload SBOM → Dependency-Track** | `cron('H 3 * * *')` **o** parámetro `RUN_DEEP_SCA=true` | Dependency-Check **rompe** si CVSS ≥ 7; la subida a DT es **best-effort** |

**Por qué así:** el gate ligero (Trivy + SBOM) es rápido y sin infra externa, así
que corre siempre. Lo pesado —Dependency-Check con su descarga/consulta de NVD, y
la subida a Dependency-Track que exige el server arriba— corre **de noche** o
cuando lo fuerzas, para no frenar el ciclo de cada push.

### Cómo forzar el deep SCA manualmente
En Jenkins: **Build with Parameters** → marca `RUN_DEEP_SCA`. El `cron` nocturno lo
dispara solo (nota: el trigger se registra **después** de la primera corrida del
pipeline, comportamiento normal de Jenkins).

### Best-effort de Dependency-Track
La etapa de subida está envuelta en `try/catch` + `fileExists('target/bom.json')`:
si Dependency-Track **no está arriba** o falta la credencial, el build se marca
**UNSTABLE** (amarillo) en vez de **FAILURE** (rojo). Así el gate de seguridad real
(Trivy, Dependency-Check) no se ve tumbado por una dependencia de infraestructura
opcional.

Credentials Jenkins a crear (**solo** para el deep SCA): `nvd-api-key`,
`dtrack-api-key`. (`dockerhub-creds` solo si activas el push, que sigue como
placeholder). Plugins: *OWASP Dependency-Check*, *HTTP Request* (y *SonarQube
Scanner* si activas Sonar, que sigue como placeholder `when { false }`).

---

## Troubleshooting

| Síntoma | Causa / Solución |
|---|---|
| Dependency-Check tarda muchísimo o da 403 | Falta la NVD API key. Pásala con `-Dnvd.api.key=...`. La 1a descarga es lenta; luego usa caché. |
| `mvn verify` rompe el build inesperadamente | Es el gate CVSS≥7 funcionando. Revisa `target/dependency-check-report.html`, remedia o suprime. |
| Dependency-Track apiserver `unhealthy` | Necesita ≥ 4 GB RAM en Docker y 1–2 min de arranque (migración BD). Revisa `docker logs`. |
| Frontend no carga datos | `API_BASE_URL` debe ser la URL que ve el **navegador** (http://localhost:8081), no la red interna. |
| `upload-sbom.sh` HTTP 401 | API key inválida o sin permiso `BOM_UPLOAD`. |
| Trivy: `permission denied` en docker.sock | El agente/usuario debe poder acceder al socket de Docker. |
| Windows: `dockerfile` vs `Dockerfile.secure` | El FS es case-insensitive; por eso el hardened va con nombre distinto. Para usarlo, apunta `dockerfile:` de compose a `Dockerfile.secure`. |
