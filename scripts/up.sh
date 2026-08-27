#!/bin/bash
# ==============================================================================
# Script: up.sh
# Descripción: Construye la imagen de Java (si hay cambios) y levanta toda la
#              infraestructura (Broker y Suscriptor) en segundo plano mediante
#              docker-compose.
# ==============================================================================

echo "=========================================="
echo "    Levantando infraestructura IoTEste    "
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

echo "Iniciando contenedores (docker-compose up -d --build)..."

# Este comando construye la imagen del suscriptor Java y levanta ambos servicios
# en modo "detached" (-d, en segundo plano) para no bloquear la terminal.
docker-compose up -d --build

echo "------------------------------------------"
echo "✅ Infraestructura operativa."
echo "Broker MQTT (Mosquitto) y Suscriptor Java corriendo correctamente."
echo "Para ver en vivo lo que imprime el código Java, ejecuta: docker logs -f iot_java_subscriber"