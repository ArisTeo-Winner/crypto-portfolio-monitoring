#!/usr/bin/env bash
#
# Ejecuta el flyway-maven-plugin leyendo la conexion del .env en tiempo de ejecucion,
# sin declarar el plugin ni credenciales en el pom.xml (las migraciones las gobierna el
# Flyway de runtime de la app; esto es solo para operaciones fuera de banda: inspeccionar
# o migrar una base sin arrancar la app completa —util sobre todo para la base NATIVA—).
#
# Uso:
#   bash scripts/flyway.sh            # info (default, solo lectura: aplicadas vs pendientes)
#   bash scripts/flyway.sh info
#   bash scripts/flyway.sh migrate    # aplica las pendientes
#   bash scripts/flyway.sh validate
#   bash scripts/flyway.sh repair
#
# Apunta a SPRING_DATASOURCE_URL del .env (localhost:5432 => la base que tenga el puerto:
# nativa si Docker esta abajo, contenedor si esta arriba). Verifica con la "huella" del log.

set -euo pipefail
cd "$(dirname "$0")/.."

GOAL="${1:-info}"

if [[ ! -f .env ]]; then
  echo "ERROR: no encuentro .env en $(pwd)" >&2
  exit 1
fi

envval() { grep -E "^$1=" .env | head -1 | cut -d= -f2-; }

URL="$(envval SPRING_DATASOURCE_URL)"
DBUSER="$(envval DB_USER)"
DBPASS="$(envval DB_PASS)"

if [[ -z "$URL" || -z "$DBUSER" ]]; then
  echo "ERROR: faltan SPRING_DATASOURCE_URL o DB_USER en .env" >&2
  exit 1
fi

echo "flyway:${GOAL} -> ${URL} (user: ${DBUSER})"
exec ./mvnw "flyway:${GOAL}" \
  -Dflyway.url="$URL" \
  -Dflyway.user="$DBUSER" \
  -Dflyway.password="$DBPASS" \
  -Dflyway.locations="filesystem:src/main/resources/db/migration"
