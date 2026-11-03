#!/bin/bash
# ==============================================================================
# Script: down.sh
# Descripción: Detiene y elimina por completo los contenedores, redes y
#              VOLÚMENES persistentes de este proyecto (no toca otros proyectos).
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

echo "Destruyendo contenedores, redes y volúmenes del proyecto..."
docker compose down -v

echo "------------------------------------------"
echo "✅ Infraestructura y base de datos destruidas desde cero."
