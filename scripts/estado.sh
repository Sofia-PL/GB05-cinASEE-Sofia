#!/usr/bin/env bash
# Muestra el estado de los contenedores y comprueba los endpoints de CinAsee.
set -eo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

if ! command -v docker >/dev/null 2>&1; then
    echo "  [X] Docker no esta instalado o no esta en el PATH." >&2
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    echo "  [X] Docker no esta en marcha. Abre Docker Desktop o el daemon y vuelve a ejecutar." >&2
    exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
    echo "  [X] Se necesita 'curl' para comprobar los servicios." >&2
    exit 1
fi

if docker compose version >/dev/null 2>&1; then
    COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1 && docker-compose version >/dev/null 2>&1; then
    COMPOSE=(docker-compose)
else
    echo "  [X] No se encontro 'docker compose' ni 'docker-compose'." >&2
    exit 1
fi

compose() { "${COMPOSE[@]}" "$@"; }

echo
echo "== Contenedores =="
compose ps

CHECKS=(
    "C3 IAM|http://localhost:8083/v1/iam/.well-known/jwks.json|200"
    "C1 Cartelera|http://localhost:8081/api/v1/movies|200"
    "C2 Reservas|http://localhost:8082/v1/reservations/stats|401,403"
    "C4 Analitica|http://localhost:8084/api/metrics/occupancy?cinemaId=550e8400-e29b-41d4-a716-446655440001|401,403"
    "Gateway|http://localhost:8080/actuator/health|200"
    "Frontend|http://localhost:3000/|200"
    "Media|http://localhost:8085/media/posters/ponyo.jpg|200"
)

echo
echo "== Endpoints =="

FALLIDOS=0
for item in "${CHECKS[@]}"; do
    IFS='|' read -r NOMBRE URL ACEPTA <<< "$item"
    CODIGO=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$URL" 2>/dev/null || printf '000')
    if [[ ",$ACEPTA," == *",$CODIGO,"* ]]; then
        printf '  %-14s %-70s OK (HTTP %s)\n' "$NOMBRE" "$URL" "$CODIGO"
    else
        printf '  %-14s %-70s FALLO (HTTP %s)\n' "$NOMBRE" "$URL" "$CODIGO"
        FALLIDOS=$((FALLIDOS + 1))
    fi
done

echo
if [ "$FALLIDOS" -gt 0 ]; then
    echo "  $FALLIDOS servicio(s) no responden. Revisa 'docker compose logs'."
    exit 1
fi

echo "  Todos los servicios responden."
