#!/bin/bash
# ==============================================================================
# Script: demo-10-habitaciones.sh
# Descripción: el engine real controlando una casa simulada de 10 habitaciones
#              (10.1 kW en total) con 3.7 kW contratados, para ver cómo reparte
#              la potencia. Usa contenedores, puertos y base propios: no toca el
#              stack real ni sus datos. Requiere Docker y las imágenes construidas
#              (bash scripts/build.sh).
#
# Uso:
#   bash scripts/demo-10-habitaciones.sh [fechaHora] [factor]
#        Levanta la demo y muestra el log resumido. Ctrl+C la termina y la borra.
#        Por defecto: martes 27/10 13:00 a x60 (la punta empieza a las 17:00
#        simuladas, unos 4 minutos reales después).
#   bash scripts/demo-10-habitaciones.sh tablero
#        En otra ventana: temperatura y estado de cada habitación en vivo.
#   bash scripts/demo-10-habitaciones.sh bajar
#        Borra la demo si quedó levantada.
# ==============================================================================

cd "$(dirname "$0")/.." || exit 1

ENGINE="http://localhost:28080"
CLAVE="clave-demo"
DC="docker compose -p ecowarm-demo --env-file demo/demo.env -f docker/docker-compose.yml -f demo/compose.demo.yml"

bajar() {
    echo
    echo "Bajando la demo..."
    $DC down -v > /dev/null 2>&1
    echo "✅ Demo eliminada (el stack real no se tocó)."
}

case "$1" in
    bajar)
        bajar
        exit 0
        ;;
    tablero)
        while true; do
            clear
            curl -s http://localhost:29090/_tablero || echo "La demo no está levantada."
            echo
            echo "(se actualiza cada 2 s · Ctrl+C para salir)"
            sleep 2
        done
        ;;
esac

FECHA_HORA="${1:-2026-10-27T13:00:00-03:00}"
FACTOR="${2:-60}"

echo "=========================================="
echo "   Demo EcoWarm: 10 habitaciones, 3.7 kW  "
echo "=========================================="

trap 'exit 130' INT TERM
trap bajar EXIT

$DC up -d postgres-db mosquitto simulador engine || exit 1

echo "Esperando que el simulador cargue las 10 habitaciones en el engine..."
for _ in $(seq 1 90); do
    if curl -s -H "X-API-Key: $CLAVE" "$ENGINE/sitio" | grep -q '"room10"'; then
        break
    fi
    sleep 2
done

curl -s -X POST "$ENGINE/control/comandos" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $CLAVE" \
    -d "{\"accion\": \"INICIAR\", \"fechaHora\": \"$FECHA_HORA\", \"factorTiempo\": $FACTOR}" > /dev/null

echo "------------------------------------------"
echo "Control iniciado. Para ver el tablero en vivo, abrí otra ventana de Git Bash y corré:"
echo "    bash scripts/demo-10-habitaciones.sh tablero"
echo "Ctrl+C termina la demo y la borra."
echo "------------------------------------------"

docker logs -f demo_engine 2>&1 | grep --line-buffered -E "Sitio '|Control iniciado|\[simulado"
