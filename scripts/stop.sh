#!/bin/bash
# ==============================================================================
# Script: stop.sh
# Descripción: Detiene los contenedores en ejecución (broker y suscriptor Java)
#              sin destruirlos ni eliminar las redes creadas por Docker.
# ==============================================================================

echo "=========================================="
echo "    Deteniendo servicios de IoTEste       "
echo "=========================================="

# Navegamos al directorio donde se encuentra el docker-compose.yml
if [ -d "docker" ]; then
    cd docker
elif [ -d "../docker" ]; then
    cd ../docker
else
    echo "❌ Error: No se pudo encontrar el directorio 'docker'."
    exit 1
fi

echo "Pausando los contenedores (docker-compose stop)..."

# Este comando detiene los servicios, pero mantiene sus estados y redes intactos
docker-compose stop

echo "------------------------------------------"
echo "✅ Contenedores detenidos correctamente."
echo "Puedes volver a iniciarlos ejecutando ./scripts/up.sh"