#!/usr/bin/env bash
# Detiene la pila de CinAsee.
set -eo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

BORRAR_DATOS=0

uso() {
    cat <<'EOF'
Uso: ./scripts/detener.sh [opciones]

  --borrar-datos    Borra tambien los volumenes (docker compose down -v)
  -h, --help        Muestra esta ayuda
EOF
}

for arg in "$@"; do
    case "$arg" in
        --borrar-datos) BORRAR_DATOS=1 ;;
        -h|--help) uso; exit 0 ;;
        *) echo "Opcion desconocida: $arg"; uso; exit 2 ;;
    esac
done

if ! command -v docker >/dev/null 2>&1; then
    echo "  [X] Docker no esta instalado o no esta en el PATH." >&2
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    echo "  [X] Docker no esta en marcha. Abre Docker Desktop o el daemon y vuelve a ejecutar." >&2
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

if [ "$BORRAR_DATOS" -eq 1 ]; then
    echo "Se van a borrar los volumenes de MySQL, MongoDB, PostgreSQL, Redis y RabbitMQ."
    read -r -p "Escribe 's' para continuar: " RESPUESTA
    case "$RESPUESTA" in
        [sSyY]) ;;
        *) echo "Cancelado."; exit 0 ;;
    esac
    compose down -v --remove-orphans
    echo "CinAsee detenido y volumenes eliminados."
else
    compose down --remove-orphans
    echo "CinAsee detenido. Los datos se conservan."
fi
