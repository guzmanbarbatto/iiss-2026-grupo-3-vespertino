#!/bin/bash
# ==============================================================================
# Script: tiempo-acelerado.sh
# Descripción: Inicia el control con el reloj sincronizado a una fecha y acelerado.
# Uso:         bash scripts/tiempo-acelerado.sh [fechaHora ISO-8601] [factorTiempo]
# La clave se toma de APP_API_KEY (variable de entorno o docker/.env); nunca se escribe acá.
# ==============================================================================

FECHA_HORA="${1:-2026-10-26T16:30:00-03:00}"
FACTOR="${2:-60}"
ENGINE_URL="${ENGINE_URL:-http://localhost:8080}"

if [ -z "$APP_API_KEY" ]; then
    for env in docker/.env ../docker/.env; do
        if [ -f "$env" ]; then
            APP_API_KEY=$(grep -E '^APP_API_KEY=' "$env" | head -1 | cut -d= -f2- | tr -d '\r')
            break
        fi
    done
fi

if [ -z "$APP_API_KEY" ]; then
    echo "Error: definí APP_API_KEY (variable de entorno o en docker/.env)."
    exit 1
fi

curl -sS -X POST "$ENGINE_URL/control/comandos" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $APP_API_KEY" \
    -d "{\"accion\": \"INICIAR\", \"fechaHora\": \"$FECHA_HORA\", \"factorTiempo\": $FACTOR}"
echo
