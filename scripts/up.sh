#!/bin/bash

echo "Levantando infraestructura EcoWarm"

if [ -d "docker" ]; then
cd docker
elif [ -d "../docker" ]; then
cd ../docker
else
echo "Error: No se pudo encontrar el directorio 'docker'."
exit 1
fi

docker compose up -d --build

echo "Infraestructura operativa."
echo "Ejecuta: docker logs -f ecowarm_engine"