#!/usr/bin/env bash
# Arranca la pila completa de CinAsee con Docker Compose.
set -eo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

SIN_BUILD=0
REINICIAR=0
BORRAR_DATOS=0
NO_ABRIR=0
TIMEOUT=240

uso() {
    cat <<'EOF'
Uso: ./scripts/iniciar.sh [opciones]

  --sin-build       Reutiliza las imagenes ya construidas
  --reiniciar       Baja los contenedores antes de levantarlos (conserva datos)
  --borrar-datos    Borra los volumenes y regenera los seeds
  --no-abrir        No abre el navegador
  --timeout=SEG     Espera maxima por servicio (por defecto 240)
  -h, --help        Muestra esta ayuda
EOF
}

for arg in "$@"; do
    case "$arg" in
        --sin-build) SIN_BUILD=1 ;;
        --reiniciar) REINICIAR=1 ;;
        --borrar-datos) BORRAR_DATOS=1 ;;
        --no-abrir) NO_ABRIR=1 ;;
        --timeout=*) TIMEOUT="${arg#*=}" ;;
        -h|--help) uso; exit 0 ;;
        *) echo "Opcion desconocida: $arg"; uso; exit 2 ;;
    esac
done

if [ -t 1 ]; then
    VERDE='\033[0;32m'
    AMARILLO='\033[0;33m'
    ROJO='\033[0;31m'
    CIAN='\033[0;36m'
    GRIS='\033[0;90m'
    SIN='\033[0m'
else
    VERDE=''
    AMARILLO=''
    ROJO=''
    CIAN=''
    GRIS=''
    SIN=''
fi

titulo() { printf '\n%s== %s ==%s\n' "$CIAN" "$1" "$SIN"; }
ok() { printf '  %s[OK]%s %s\n' "$VERDE" "$SIN" "$1"; }
aviso() { printf '  %s[!]%s  %s\n' "$AMARILLO" "$SIN" "$1"; }
fallo() { printf '  %s[X]%s  %s\n' "$ROJO" "$SIN" "$1"; }

if ! command -v docker >/dev/null 2>&1; then
    fallo "Docker no esta instalado o no esta en el PATH."
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    fallo "Docker no esta en marcha. Abre Docker Desktop o el daemon y vuelve a ejecutar."
    exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
    fallo "Se necesita 'curl' para comprobar los servicios."
    exit 1
fi

if docker compose version >/dev/null 2>&1; then
    COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1 && docker-compose version >/dev/null 2>&1; then
    COMPOSE=(docker-compose)
else
    fallo "No se encontro 'docker compose' ni 'docker-compose'."
    exit 1
fi

compose() { "${COMPOSE[@]}" "$@"; }

ok "Docker y Docker Compose disponibles"

if [ -z "$(compose ps -q 2>/dev/null)" ]; then
    PUERTOS=(3000 8080 8081 8082 8083 8084 8085 3306 6379 27017 5433 5434 5672 15672)
    OCUPADOS=()
    if command -v nc >/dev/null 2>&1; then
        for p in "${PUERTOS[@]}"; do
            if nc -z 127.0.0.1 "$p" >/dev/null 2>&1; then
                OCUPADOS+=("$p")
            fi
        done
    fi
    if [ ${#OCUPADOS[@]} -gt 0 ]; then
        aviso "Puertos ya ocupados: ${OCUPADOS[*]}. Si pertenecen a otro programa, libéralos antes de continuar."
    fi
fi

if [ "$BORRAR_DATOS" -eq 1 ]; then
    aviso "Se van a borrar los volumenes de MySQL, MongoDB, PostgreSQL, Redis y RabbitMQ."
    read -r -p "Escribe 's' para continuar: " RESPUESTA
    case "$RESPUESTA" in
        [sSyY]) ;;
        *) echo "Cancelado."; exit 0 ;;
    esac
    compose down -v --remove-orphans
    ok "Volumenes eliminados"
elif [ "$REINICIAR" -eq 1 ]; then
    compose down --remove-orphans
    ok "Contenedores detenidos (datos conservados)"
fi

ARGS_UP=(up -d --remove-orphans)
if [ "$SIN_BUILD" -eq 0 ]; then
    ARGS_UP+=(--build)
fi

titulo "Construyendo y levantando contenedores"
printf '  %sLa primera vez puede tardar varios minutos.%s\n' "$GRIS" "$SIN"
compose "${ARGS_UP[@]}"
ok "Contenedores lanzados"

CHECKS=(
    "C3 IAM|http://localhost:8083/v1/iam/.well-known/jwks.json|200|componente3-usuarios"
    "C1 Cartelera|http://localhost:8081/api/v1/movies|200|componente1-cartelera"
    "C2 Reservas|http://localhost:8082/v1/reservations/stats|401,403|componente2-reservas"
    "C4 Analitica|http://localhost:8084/api/metrics/occupancy?cinemaId=550e8400-e29b-41d4-a716-446655440001|401,403|componente4-analitica"
    "Gateway|http://localhost:8080/actuator/health|200|componente5-gateway"
    "Frontend|http://localhost:3000/|200|frontend"
    "Media|http://localhost:8085/media/posters/ponyo.jpg|200|media-server"
)

codigo_http() {
    curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$1" 2>/dev/null || printf '000'
}

titulo "Esperando a que los servicios respondan"
INICIO=$(date +%s)
LIMITE=$((INICIO + TIMEOUT))
PENDIENTES=("${CHECKS[@]}")

while [ ${#PENDIENTES[@]} -gt 0 ] && [ "$(date +%s)" -lt "$LIMITE" ]; do
    SIGUIENTES=()
    for item in "${PENDIENTES[@]}"; do
        IFS='|' read -r NOMBRE URL ACEPTA SERVICIO <<< "$item"
        CODIGO=$(codigo_http "$URL")
        if [[ ",$ACEPTA," == *",$CODIGO,"* ]]; then
            ok "$NOMBRE responde (HTTP $CODIGO)"
        else
            SIGUIENTES+=("$item")
        fi
    done
    PENDIENTES=("${SIGUIENTES[@]}")
    if [ ${#PENDIENTES[@]} -gt 0 ]; then
        sleep 3
    fi
done

if [ ${#PENDIENTES[@]} -gt 0 ]; then
    NOMBRES=()
    for item in "${PENDIENTES[@]}"; do
        IFS='|' read -r NOMBRE URL ACEPTA SERVICIO <<< "$item"
        NOMBRES+=("$NOMBRE")
    done
    fallo "No respondieron a tiempo: ${NOMBRES[*]}"
    echo
    compose ps
    for item in "${PENDIENTES[@]}"; do
        IFS='|' read -r NOMBRE URL ACEPTA SERVICIO <<< "$item"
        echo
        aviso "Ultimos logs de $SERVICIO:"
        compose logs --tail=100 "$SERVICIO"
    done
    exit 1
fi

SEGUNDOS=$(( $(date +%s) - INICIO ))
titulo "CinAsee listo en ${SEGUNDOS} s"

echo "  Frontend      http://localhost:3000"
echo "  Gateway       http://localhost:8080"
echo "  C1 Cartelera  http://localhost:8081/api/v1"
echo "  C2 Reservas   http://localhost:8082/v1"
echo "  C3 IAM        http://localhost:8083/v1/iam"
echo "  C4 Analitica  http://localhost:8084/api"
echo "  RabbitMQ UI   http://localhost:15672  (cinasee / cinasee)"
echo "  Media         http://localhost:8085/media/posters"
echo
echo "  Credenciales demo:"
echo "    admin@cinasee.com    / Admin123!"
echo "    staff@cinasee.com    / Staff123!"
echo "    usuario@cinasee.com  / Usuario123!"
echo
echo "  Cine demo (Madrid): 550e8400-e29b-41d4-a716-446655440001"
printf '  %sNota: la analitica empieza vacia hasta que se complete una compra.%s\n' "$GRIS" "$SIN"
echo
echo "  Parar:   ./scripts/detener.sh"
echo "  Estado:  ./scripts/estado.sh"

CARTELERA=$(curl -s --max-time 5 'http://localhost:8081/api/v1/movies?inShowcase=true' 2>/dev/null || true)
if [ -z "$CARTELERA" ] || [ "$CARTELERA" = "[]" ]; then
    echo
    aviso "No hay peliculas en cartelera: las sesiones del seed caducan a los ~3 dias."
    aviso "Ejecuta './scripts/iniciar.sh --borrar-datos' para regenerar el seed."
fi

if [ "$NO_ABRIR" -eq 0 ]; then
    if command -v open >/dev/null 2>&1; then
        open http://localhost:3000
    elif command -v xdg-open >/dev/null 2>&1; then
        xdg-open http://localhost:3000 >/dev/null 2>&1 || true
    fi
fi
