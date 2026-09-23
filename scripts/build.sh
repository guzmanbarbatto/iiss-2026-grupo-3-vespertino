#!/bin/bash

echo "Construyendo las imágenes de los contenedores..."

# Construcción de los servicios definidos en el compose (Generador, Subscriber)
docker compose -f docker/docker-compose.yml build

# Construcción de la API apuntando el contexto a la raíz (.) para que Maven lea el POM padre
docker build -t ecowarm-api:latest -f api/Dockerfile .

echo "Pipeline completado. Imágenes listas para levantar."