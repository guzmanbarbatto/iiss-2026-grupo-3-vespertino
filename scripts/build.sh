#!/bin/bash

echo "Construyendo las imágenes de los contenedores..."

# Construcción de los servicios definidos en el compose 
docker compose -f docker/docker-compose.yml build

docker build -t ecowarm-engine:latest -f modules/engine/Dockerfile .

echo "Pipeline completado. Imágenes listas para levantar."