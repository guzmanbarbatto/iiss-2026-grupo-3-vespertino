#!/bin/bash
# ==============================================================================
# Script: down.sh
# Descripción: Detiene y elimina por completo los contenedores, redes y
#              VOLÚMENES persistentes
# ==============================================================================

echo "=========================================="
echo "    Deteniendo y limpiando IoTEste        "
echo "=========================================="

if [ -d "docker" ]; then
    cd docker
elif [ -d "../docker" ]; then
    cd ../docker
else
    echo "Error: No se pudo encontrar el directorio 'docker'."
    exit 1
fi

echo "Destruyendo contenedores, redes y volúmenes..."
docker compose down -v

echo "Purgando volúmenes huérfanos (limpieza profunda)..."
docker volume prune -f

echo "------------------------------------------"
echo "✅ Infraestructura y base de datos destruidas desde cero."