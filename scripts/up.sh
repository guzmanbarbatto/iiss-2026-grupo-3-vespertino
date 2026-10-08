#!/bin/bash
# ==============================================================================
# Script: up.sh
# Descripción: Construye las imágenes y levanta toda la infraestructura
#              (Broker, Controlador Engine y Simulador) en segundo plano.
# ==============================================================================

echo "=========================================="
echo "    Levantando infraestructura EcoWarm    "
echo "=========================================="

# Navegamos al directorio donde se encuentra el docker-compose.yml
if [ -d "docker" ]; then
    cd docker
elif [ -d "../docker" ]; then
    cd ../docker
else
    echo "Error: No se pudo encontrar el directorio 'docker'."
    exit 1
fi

echo "Iniciando contenedores (docker-compose up -d --build)..."

# Este comando construye las imágenes y levanta los servicios
docker-compose up -d --build

echo "------------------------------------------"
echo "✅ Infraestructura operativa."
echo "Broker MQTT, Controlador (Engine) y Simulador corriendo correctamente."
echo "Para ver en vivo los logs del controlador, ejecuta: docker logs -f ecowarm-engine"