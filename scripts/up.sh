#!/bin/bash
# ==============================================================================
# Script: up.sh
# Descripción: Levanta la infraestructura de EcoWarm desde cero.
# ==============================================================================

echo "=========================================="
echo "    Levantando infraestructura EcoWarm    "
echo "=========================================="

if [ -d "docker" ]; then
    cd docker
elif [ -d "../docker" ]; then
    cd ../docker
else
    echo "Error: No se pudo encontrar el directorio 'docker'."
    exit 1
fi

docker compose up -d --build

echo "------------------------------------------"
echo "✅ Infraestructura operativa."