1. **Resumen General del Estado Actual del Backend**

   El backend está en un estado de **preproducción avanzada**, no en estado de producción real. La base técnica es buena: hay modularidad real, separación por dominios (`asset`, `marketdata`, `portfolio`, `transaction`, `user`, `shared`), uso de `Spring Modulith`, reglas de `ArchUnit`, integración con proveedores externos, reconciliación de proyecciones, JWT + refresh tokens, y una batería de pruebas bastante por encima del promedio de un proyecto típico.

   Aun así, si lo evalúo con criterio de sistemas financieros listos para producción, el diagnóstico honesto es este: **la arquitectura de software está bastante madura, pero la arquitectura operativa, la gobernanza de datos, la observabilidad, la estrategia de despliegue, la resiliencia y la disciplina de cambios de esquema todavía no están al nivel de producción**.

   El problema principal no es "el código no funciona". El problema principal es: **funciona bien como backend serio de desarrollo/preproducción, pero todavía tiene varios huecos que en producción te van a pegar en disponibilidad, trazabilidad, control del riesgo y operabilidad**.

2. **Aspectos Bien Implementados**

   - **Modularidad real del backend**. No es un monolito desordenado. Hay separación explícita por módulos, límites reforzados con `Spring Modulith`, puertos expuestos con `package-info.java` y `NamedInterface`, y verificación arquitectónica automatizada.
   - **Buen avance hacia arquitectura hexagonal**. Especialmente en `portfolio`, `transaction`, `marketdata` y `user`, ya existen `application.port.in`, `application.port.out`, adapters `infrastructure.inbound` y `infrastructure.outbound`. No está perfecto, pero la dirección es correcta y defendida por tests.
   - **Flujo de autenticación sólido para una app de negocio**. JWT, refresh tokens, hashing adicional de refresh token, soporte OAuth2/Google, invalidación/revocación, auditoría de auth y separación entre `AuthService`, `TokenService`, `RefreshTokenStoreService`.
   - **Buenas prácticas AppSec ya incorporadas**. Headers HTTP seguros en backend y frontend; redacción de secretos en `toString()`; `Problem Details` tipo RFC 9457; control de acceso por rol; CORS explícito; `HttpOnly` y `Secure` para cookie JWT.
   - **Consistencia de portfolio mejor pensada que en muchos trackers**. Haber movido la verdad al historial de `transaction` y tratar `portfolio_entry` como proyección derivada es una decisión correcta. La reconciliación automática después de `PUT` y `DELETE` reduce drift.
   - **Cobertura de calidad superior al promedio**. Hay tests unitarios, de integración, de seguridad, de arquitectura, de Modulith, pruebas de adapters HTTP y validaciones de AppSec headers. También hay integración con SonarQube, JaCoCo, Spotless y Checkstyle.
   - **Integraciones externas razonablemente encapsuladas**. CoinMarketCap, CoinGecko, AlphaVantage y Massive están modelados como adapters, no como llamadas dispersas. Ya existe timeout en varios clientes y se introdujo cache para histórico crypto.
   - **Protección contra algunos problemas operativos reales**. Se corrigieron conflictos optimistas en reconciliación, se redujo ruido de logging en fallos repetitivos, se implementó fallback a proyección persistida en ciertos read paths y se evitó parte del acoplamiento entre módulos.
   - **OpenAPI y Swagger existen**. No están completos ni del todo coherentes, pero no estás partiendo de cero: ya hay `springdoc`, `SwaggerConfig`, anotaciones en controllers y un `openapi.yaml` estático.

3. **Lo que FALTA o está INCOMPLETO para Producción**

   - **Migraciones de base de datos realmente operativas**. Este es uno de los gaps más críticos. En `src/main/resources/application.properties` tienes `spring.flyway.enabled=true`, pero no existe `src/main/resources/db/migration`, y además la propiedad de ubicación está mal escrita: `spring.flyway.locations=yway.locations:classpath:db/migration`. En paralelo, el perfil por defecto sigue con `spring.jpa.hibernate.ddl-auto=update`. Eso significa que hoy el control de esquema no está realmente gobernado. Para producción esto es inaceptable.
   - **Despliegue no está usando perfil de producción de forma explícita**. `src/main/resources/application-prod.properties` tiene ajustes mejores (`ddl-auto=validate`, `open-in-view=false`, `show-sql=false`), pero `docker-compose.yml` no define `SPRING_PROFILES_ACTIVE=prod`. Con el despliegue actual, es muy probable que el servicio arranque con la configuración insegura o de desarrollo.
   - **La configuración base está sucia y con deuda operativa**. `application.properties` mezcla defaults de desarrollo, comentarios temporales, propiedades no usadas o dudosas (`msg.assets`, `posgresql.url`, `API_ALPHAVANTAGE_*` como alias), logging comentado, y settings que no deberían ser el baseline de runtime. Eso aumenta el riesgo de arrancar mal el servicio.
   - **Documentación OpenAPI/Swagger incompleta y parcialmente desalineada**. `SwaggerConfig` existe, pero el customizer solo enriquece un subconjunto de rutas (`/api/v1/users`, `/api/v1/me/transactions`, `/api/v1/assets`). Además existe `src/main/resources/static/openapi.yaml`, pero está desfasado respecto al backend actual: usa rutas antiguas como `/portfolio/transactions`, estructuras de respuesta envueltas, y no parece ser la verdad del sistema. En producción esto genera documentación engañosa.
   - **Falta definición formal de esquemas de seguridad en OpenAPI generado**. No encontré configuración explícita de `SecurityScheme` para bearer token en el OpenAPI dinámico. El YAML estático sí habla de `bearerAuth`, pero al estar desalineado no sirve como contrato confiable.
   - **No hay pruebas de rendimiento ni load testing**. No hay evidencia de `k6`, `Gatling`, `JMeter` o pruebas de soak, spikes, concurrencia real o comportamiento bajo bursts de escritura financiera. Dado que `portfolio` hace cálculos agregados y reconciliación, esto es una ausencia importante.
   - **Observabilidad incompleta**. Tienes Actuator y algo de Micrometer en `CoinMarketCapAdapter`, pero no hay instrumentación transversal del sistema. No vi:
     - `micrometer-registry-prometheus`
     - endpoint Prometheus configurado
     - métricas de latencia por endpoint
     - métricas de error por caso de uso
     - métricas de reconciliación
     - métricas de rate limiting
     - métricas de caché útiles para negocio
   - **Tracing distribuido ausente**. No vi integración con OpenTelemetry, Zipkin, Tempo, Jaeger, Sleuth o equivalente. `ApiProblemDetailsFactory` usa `MDC.get("traceId")`, pero no encontré una estrategia end-to-end de propagación de trace/span IDs entre requests, logs y llamadas salientes.
   - **Logging estructurado ausente**. `src/main/resources/logback-spring.xml` usa texto plano. No hay JSON logging, no hay esquema estandarizado de campos, no hay integración con ELK/Loki/Datadog, y la estrategia de correlación no está completa. Además, la configuración de logging entre `application-prod.properties` y `logback-spring.xml` es inconsistente.
   - **Monitoreo y alerting no evidenciados**. No hay dashboards, alertas por SLA, alertas de error rate, alertas de rate limit externo, alertas de Redis/DB saturation, alertas de reconciliación fallida, ni runbooks asociados.
   - **No hay estrategia de backup/recovery**. No vi:
     - política de backups de PostgreSQL
     - snapshots
     - PITR/WAL archiving
     - restauración documentada
     - pruebas de restore
     - RPO/RTO definidos
   - **Rate limiting no es production-grade**. Los limiters (`LoginRateLimiter`, `AssetSearchRateLimiter`, etc.) son `ConcurrentHashMap` en memoria por instancia. Eso implica:
     - no escalan horizontalmente
     - se resetean al reiniciar
     - se pueden evadir distribuyendo requests entre instancias
     - dependen de `X-Forwarded-For` leído directamente, lo que abre spoofing si el proxy no sanea cabeceras
   - **El `HttpFirewall` está demasiado relajado**. En `SecurityConfig` se permiten:
     - encoded slash
     - encoded percent
     - semicolon
     - backslash
     - encoded period  
     Para una API productiva esto amplía superficie de ataque sin una justificación fuerte.
   - **Modelo de sesión/token ambiguo**. `AuthController` devuelve tokens en cuerpo JSON y además crea cookie `jwt`. Esto mezcla dos estrategias:
     - SPA con token en memoria/local storage
     - cookie-based auth  
     Además, la cookie no muestra control explícito de `SameSite`, y el refresh token va en un header custom `X-Refresh-Token`. No es necesariamente incorrecto, pero sí está incompleto como diseño de seguridad de producción.
   - **Falta endurecimiento de cookies y estrategia CSRF clara si se van a usar cookies reales**. Ahora mismo `csrf()` está deshabilitado de forma global. Si el futuro del cliente es header-based puro, bien. Si se va a usar cookie JWT realmente, la estrategia está incompleta.
   - **No hay auditoría financiera seria de transacciones**. Hay auditoría de auth/usuarios, pero no vi un audit trail robusto para:
     - creación de transacción
     - edición de transacción
     - borrado de transacción
     - cambios críticos de holdings  
     En un tracker financiero real, esto es un gap fuerte.
   - **No hay idempotency keys para writes financieros**. `POST /buy`, `POST /sell`, `POST /transfer`, `PUT`, `DELETE` no están protegidos contra retries de cliente, reenvíos de gateway o doble submit del frontend. Eso en producción puede duplicarte operaciones.
   - **El modelo no es ledger-grade**. `Transaction` no tiene `@Version`, no hay append-only ledger, no hay event sourcing, y se permite editar transacciones históricas directamente. Para un portfolio tracker retail puede ser aceptable, pero no para un entorno financiero más serio o auditable.
   - **Read path con efectos secundarios**. `GET /api/v1/me/portfolio` puede reconciliar y escribir `portfolio_entry`. Aunque ya mitigaste varios problemas, un `GET` que muta estado sigue siendo un anti-pattern operativo y de escalabilidad.
   - **No hay scheduler realmente habilitado para `PriceUpdateService`**. Existe `@Scheduled(fixedRate = 300000)` en `PriceUpdateService`, pero no vi `@EnableScheduling` en `CryptoPortfolioMonitoringApplication` ni en otra configuración central. Muy probablemente ese refresco periódico no está corriendo.
   - **Cobertura funcional de assets todavía es parcial**. La serie histórica con CoinGecko depende de mapping `symbol -> assetId`, y hoy solo está bien cubierta para un subconjunto. Para activos como `HYPE`, `SOL`, `TRX`, `XMR`, la experiencia todavía puede degradar a fallback. Eso no es “listo para producción” si tu promesa de producto cubre multi-asset real.
   - **Soporte multi-activo todavía no está completo de punta a punta**. `INDEX` y parte de `ETF` no tienen pricing y operaciones al mismo nivel que `CRYPTO` y `STOCK`. La plataforma no está lista para presentarse como sistema multi-asset completo en producción.
   - **Resilience pattern incompleto**. Hay timeouts y fallback, pero no vi:
     - circuit breakers
     - bulkheads
     - retry policy formal
     - backoff
     - quota-aware routing
     - degradación controlada por proveedor  
     Para upstreams con rate limit esto es un gap.
   - **Exposición pública de Swagger/Actuator en seguridad base**. `SecurityConfig` permite `/swagger-ui/**`, `/v3/api-docs/**` y `/actuator/health` públicamente. Esto puede ser válido en algunos entornos, pero en producción debe estar deliberadamente gobernado, no abierto por defecto sin más estrategia.
   - **No hay evidencia de hardening CloudSec**. No hay IaC, políticas de red, WAF, secret manager, IAM de mínimo privilegio, rotación de secretos, container hardening, image scanning, SBOM o políticas de supply chain visibles en el repo.
   - **PostgreSQL y Redis en Compose están expuestos y blandos**. `docker-compose.yml` publica `5432` y `6379` al host y Redis no tiene auth visible. Está bien para desarrollo, no para producción.
   - **No hay estrategia formal de desastre, restore y continuidad operativa**. Tampoco hay runbooks operativos de incidentes.
   - **No hay pruebas contractuales interservicio ni tests de compatibilidad API para clientes**. Tienes muchas pruebas buenas, pero no vi una estrategia de contract testing tipo consumer-driven.
   - **No hay validación explícita de readiness/liveness probes**. Actuator existe, pero no vi configuración de grupos `liveness` y `readiness` ni probes productivos bien definidos.
   - **No hay control explícito de versionado de API** más allá del path `/api/v1`. No vi política de deprecación, compatibilidad y cambios de contrato.

4. **Riesgos Técnicos y de Seguridad Críticos si se lanza a producción**

   - **Drift y corrupción de esquema**. Con `ddl-auto=update` como default y sin migraciones reales, puedes terminar con cambios de esquema no controlados, fallos al arrancar distintas versiones, y rollbacks imposibles.
   - **Despliegue con configuración equivocada**. Si Compose o el despliegue real no activa `prod`, el sistema puede salir con SQL visible en logs, `open-in-view=true`, cambios automáticos de esquema y defaults inseguros.
   - **Pérdida de control de cambios en base de datos**. No tener Flyway/Liquibase de verdad elimina trazabilidad, rollback y reproducibilidad del estado de datos.
   - **Abuso de la API pese al rate limiting “existente”**. Los limiters en memoria no sirven como defensa robusta en producción multi-instancia. Un atacante o bot los puede evadir con facilidad.
   - **Bypass o evasión vía cabeceras y path handling**. Leer `X-Forwarded-For` directamente y relajar tanto el `StrictHttpFirewall` sube el riesgo de spoofing y rutas ambiguas.
   - **Falta de idempotencia en operaciones financieras**. Un retry de red, un doble clic o un reenvío automático puede duplicar compras/ventas/transferencias.
   - **Auditoría insuficiente para incidentes financieros**. Si un usuario dice “mi operación cambió” o “desapareció una transacción”, hoy no tienes un rastro financiero robusto e inmutable para reconstruir responsabilidad y secuencia exacta.
   - **Incidentes ciegos**. Sin tracing, métricas completas, dashboards y alertas, un problema real de producción te va a dejar viendo logs planos y síntomas, no causas.
   - **Dependencia fuerte de proveedores externos sin protección suficiente**. Si CoinGecko, CoinMarketCap, AlphaVantage o Massive se degradan o rate-limitean, el sistema tiene fallback parcial, pero no un modelo fuerte de resiliencia operacional.
   - **`GET` con side effects**. Si el read path sigue reconciliando y escribiendo, un spike de lecturas puede generar contención, conflictos optimistas o presión innecesaria en base de datos.
   - **Background job posiblemente inactivo sin que nadie lo note**. `PriceUpdateService` probablemente no corre hoy, así que puedes tener datos stale sin saberlo.
   - **Riesgo de documentación falsa**. Tener OpenAPI estático y dinámico desalineados lleva a clientes mal integrados, QA incorrecto y contratos rotos sin visibilidad.
   - **Riesgo CloudSec por secretos y servicios base**. Secrets por `.env`, Redis abierto, PostgreSQL expuesto, ausencia de rotación y de control de red son un mal punto de partida para un entorno real.
   - **Ambigüedad del modelo de autenticación en cliente**. Mezclar cookie y tokens en body, sin una política cerrada y documentada, crea superficies grises de sesión y seguridad.

5. **Mejoras Recomendadas por Prioridad**

   **Alta prioridad**
   - **Implantar migraciones reales con Flyway o Liquibase**. Justificación: hoy el backend no tiene una historia reproducible del esquema y depende peligrosamente de `ddl-auto=update`. Esfuerzo: medio. Herramienta recomendada: `Flyway`.
   - **Forzar perfil de producción en despliegues y endurecer config base**. Justificación: `application-prod.properties` existe pero no se activa en `docker-compose.yml`; esto puede llevar defaults inseguros a runtime. Esfuerzo: bajo. Herramienta recomendada: `SPRING_PROFILES_ACTIVE=prod`, perfiles Spring, configuración externa por entorno.
   - **Eliminar `ddl-auto=update` de cualquier ruta de producción**. Justificación: es uno de los anti-patterns más peligrosos para sistemas con datos financieros. Esfuerzo: bajo. Herramienta recomendada: `Flyway + ddl-auto=validate`.
   - **Implementar idempotency keys para endpoints de creación y modificación financiera**. Justificación: evita duplicación de operaciones por retry o doble submit. Esfuerzo: medio. Herramienta recomendada: tabla `idempotency_keys`, filtro/interceptor, constraint por `(user, key, route)`.
   - **Agregar auditoría financiera para create/update/delete de transacciones**. Justificación: hoy auditas auth, pero no suficientemente el núcleo financiero. Esfuerzo: medio. Herramienta recomendada: `AuditLogService` extendido o tabla separada `transaction_audit_log`.
   - **Reemplazar rate limiting en memoria por rate limiting distribuido**. Justificación: el actual no escala y no protege de verdad en producción. Esfuerzo: medio. Herramienta recomendada: `Bucket4j + Redis` o API gateway/WAF.
   - **Endurecer `StrictHttpFirewall` y revisar trust proxy**. Justificación: permitir encoded slash, percent, semicolon y backslash sin necesidad fuerte es un riesgo. Esfuerzo: bajo. Herramienta recomendada: `StrictHttpFirewall` más cerrado, configuración explícita de proxy confiable.
   - **Cerrar estrategia de sesión/autenticación**. Justificación: el sistema mezcla token body y cookie JWT; esto debe decidirse y documentarse. Esfuerzo: medio. Herramienta recomendada: arquitectura clara de auth, cookies con `SameSite`, o tokens en header exclusivamente.
   - **Observabilidad mínima de producción**. Justificación: sin métricas, tracing y alertas no hay operación segura. Esfuerzo: medio-alto. Herramienta recomendada: `Micrometer + Prometheus + Grafana + OpenTelemetry + Tempo/Jaeger`.
   - **Separar reconciliación correctiva del read path**. Justificación: un `GET` no debería mutar estado en producción. Esfuerzo: medio-alto. Herramienta recomendada: job de reconciliación explícito, hook post-write, endpoint admin de repair.

   **Media prioridad**
   - **Completar y unificar documentación OpenAPI**. Justificación: hoy existe, pero no es fuente única ni completamente confiable. Esfuerzo: medio. Herramienta recomendada: `springdoc-openapi` como fuente de verdad, eliminar o regenerar `static/openapi.yaml`.
   - **Agregar pruebas de rendimiento y carga**. Justificación: no sabes todavía cómo se comportan `portfolio`, `transactions` y `holdings-performance` bajo concurrencia real. Esfuerzo: medio. Herramienta recomendada: `k6` o `Gatling`.
   - **Agregar circuit breakers y bulkheads en integraciones externas**. Justificación: tienes fallback, pero no protección completa ante degradación de proveedores. Esfuerzo: medio. Herramienta recomendada: `Resilience4j`.
   - **Implementar logging estructurado JSON y correlación end-to-end**. Justificación: hoy los logs son útiles para desarrollo, no para operación seria. Esfuerzo: medio. Herramienta recomendada: `logstash-logback-encoder`, `MDC`, `OpenTelemetry`.
   - **Configurar readiness/liveness y health groups**. Justificación: Actuator existe, pero no está claramente listo para orquestadores. Esfuerzo: bajo-medio. Herramienta recomendada: `management.endpoint.health.probes.enabled=true`.
   - **Habilitar o rediseñar `PriceUpdateService`**. Justificación: hoy el job probablemente no corre por falta de `@EnableScheduling`, o al menos no hay evidencia de que esté operativo. Esfuerzo: bajo. Herramienta recomendada: `@EnableScheduling` o scheduler externo.
   - **Ampliar cobertura de mapeo `symbol -> CoinGecko assetId`**. Justificación: el histórico real aún no cubre todos los activos relevantes del producto. Esfuerzo: medio. Herramienta recomendada: catálogo persistido o tabla de mapping mantenible.
   - **Agregar contract tests y pruebas de compatibilidad API**. Justificación: el sistema ya tiene muchos tests, pero falta blindaje de contrato para clientes. Esfuerzo: medio. Herramienta recomendada: `Spring Cloud Contract` o similar.

   **Baja prioridad**
   - **Limpiar deuda de configuración y comentarios temporales**. Justificación: no rompe producción por sí mismo, pero complica operar y auditar. Esfuerzo: bajo. Herramienta recomendada: refactor de config y `.properties`.
   - **Mejorar higiene del repo**. Justificación: hay artefactos de build/logs en raíz del repo que no deberían estar ahí. Esfuerzo: bajo. Herramienta recomendada: `.gitignore`, limpieza de workspace, convención de outputs.
   - **Consolidar internacionalización y consistencia de naming**. Justificación: hay mezcla de español/inglés, nombres antiguos y endpoints heredados. Esfuerzo: medio. Herramienta recomendada: refactor progresivo de naming y docs.
   - **Revisar estrategia de static docs y README técnico**. Justificación: la documentación existe, pero puede ordenarse mejor para onboarding y operación. Esfuerzo: bajo. Herramienta recomendada: `README`, ADRs, docs técnicas por módulo.

6. **Plan de Acción Concreto y Ordenado para Llegar a Producción**

   1. **Congelar configuración base y activar `prod` de verdad**. Añadir `SPRING_PROFILES_ACTIVE=prod` en despliegue, quitar defaults peligrosos del baseline, y validar arranque con `ddl-auto=validate`.
   2. **Introducir Flyway como fuente de verdad del esquema**. Crear baseline migration, migraciones incrementales, validación en CI y rollback plan.
   3. **Definir estrategia de secretos y runtime config**. Sacar secretos de `.env` para producción y moverlos a Secret Manager/Vault/KMS equivalente.
   4. **Cerrar modelo de autenticación del cliente**. Elegir estrategia final: header-based o cookie-based. Si hay cookies, definir `SameSite`, CSRF y política de renovación. Si no, eliminar ambigüedad.
   5. **Implementar idempotencia en endpoints financieros**. Empezar por `POST /buy`, `POST /sell`, `POST /transfer`, luego `PUT`.
   6. **Agregar auditoría financiera de alto valor**. Registrar create/update/delete de transacciones con actor, timestamp, payload resumido, antes/después y origen.
   7. **Sacar reconciliación correctiva del read path como comportamiento principal**. Mantener fallback de emergencia si quieres, pero mover la consistencia normal a write side y jobs explícitos.
   8. **Sustituir rate limiters locales por una estrategia distribuida**. Redis o gateway, con llaves por identidad y proxy trust bien definido.
   9. **Endurecer superficie HTTP**. Revisar `StrictHttpFirewall`, visibilidad de Swagger/Actuator en producción, y política de exposición pública.
   10. **Montar observabilidad real**. Métricas de negocio y técnicas, Prometheus/Grafana, trazas distribuidas, dashboards y alertas.
   11. **Agregar resiliencia real a proveedores**. Circuit breakers, timeouts homogéneos, budgets, fallback explícito, métricas por proveedor y alarmas de saturación.
   12. **Ejecutar pruebas de carga y concurrencia**. Especialmente sobre login, refresh, `me/portfolio`, `me/transactions`, `holdings-performance`, edición y borrado.
   13. **Completar y alinear OpenAPI**. Hacer del spec generado la fuente única, documentar auth, errores, ejemplos y contratos actuales.
   14. **Diseñar backup y recovery**. Definir backups de PostgreSQL, retención, restore automatizado, prueba de recuperación y RPO/RTO.
   15. **Hacer una prueba de preproducción real**. Carga, failover de proveedor, caída de Redis, caída de DB secundaria, replay de incidentes y validación de runbooks.
   16. **Solo después de eso, preparar el release productivo** con checklist formal y aprobación operativa.

7. **Checklist de Cumplimiento para Producción**

   - `SPRING_PROFILES_ACTIVE=prod` activado en todos los despliegues.
   - `spring.jpa.hibernate.ddl-auto=validate` en producción.
   - Flyway o Liquibase operativo con migraciones versionadas y probadas.
   - No existe dependencia de `ddl-auto=update` en ningún entorno productivo.
   - OpenAPI/Swagger refleja exactamente los endpoints reales y sus contratos actuales.
   - Seguridad OpenAPI documenta `Bearer`/auth real y errores RFC 9457.
   - Secrets gestionados fuera del repo y del `.env` local.
   - Rotación de secretos definida.
   - JWT/refresh/cookies con estrategia final cerrada y documentada.
   - Rate limiting distribuido y no solo en memoria.
   - `X-Forwarded-For` y proxy trust correctamente gobernados.
   - `StrictHttpFirewall` endurecido.
   - Swagger y Actuator expuestos deliberadamente, no por accidente.
   - Métricas por endpoint, por proveedor externo y por operación financiera disponibles.
   - Exportación de métricas a Prometheus o equivalente.
   - Tracing distribuido operativo con `traceId`/`spanId`.
   - Logging estructurado JSON disponible en producción.
   - Dashboards y alertas mínimas configuradas.
   - Health, readiness y liveness probes definidos y validados.
   - Backups de base de datos automatizados.
   - Restore probado en entorno de ensayo.
   - RPO y RTO definidos.
   - Auditoría de auth y auditoría financiera activas.
   - Create/update/delete de transacciones auditados.
   - Idempotency keys implementadas en operaciones financieras.
   - Pruebas de carga ejecutadas con resultados aceptables.
   - Pruebas de concurrencia ejecutadas sobre portfolio y transacciones.
   - Resilience4j o equivalente configurado para upstreams externos.
   - Circuit breaker y timeouts validados contra fallos reales de proveedores.
   - Mapping de activos históricos completo para los assets que el producto promete soportar.
   - Features incompletas de `INDEX`/`ETF` claramente cerradas o retiradas antes de producción.
   - `PriceUpdateService` o su reemplazo está realmente habilitado y monitoreado.
   - Ningún `GET` crítico muta estado como mecanismo normal de operación.
   - CI/CD ejecuta tests, ArchUnit, Modulith, SonarQube y quality gate obligatoria.
   - Existe runbook operativo para incidentes de DB, Redis, provider outage y expiración/token issues.
   - Existe entorno de preproducción parecido a producción donde todo esto ya fue ensayado con éxito.