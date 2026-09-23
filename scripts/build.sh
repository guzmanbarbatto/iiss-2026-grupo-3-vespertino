#!/bin/bash

echo "Inicializando el motor de construcción (BuildKit) para la resolución asíncrona de dependencias Maven..."
echo "[Iteración 3] Procesando módulos: Generador, Suscriptor y la nueva REST API/Controlador..."

# Construcción de los servicios definidos en el compose (Generador, Subscriber)
docker compose -f docker/docker-compose.yml build

# Construcción de la API apuntando el contexto a la raíz (.) para que Maven lea el POM padre
docker build -t ecowarm-api:latest -f api/Dockerfile .

echo "Pipeline completado. Imágenes listas para levantar."