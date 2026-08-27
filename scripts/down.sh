#!/bin/bash
# ==============================================================================
# Script: down.sh
# Descripción: Detiene y elimina por completo los contenedores de la infraestructura,
#              así como las redes creadas por docker-compose.
# ==============================================================================

echo "=========================================="
echo "    Deteniendo y limpiando IoTEste        "
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

echo "Destruyendo contenedores y redes (docker-compose down)..."

# Este es el comando clave que destruye lo que levantamos en el Sprint 1
docker-compose down

echo "------------------------------------------"
echo "✅ Infraestructura destruida correctamente."