#!/bin/bash

echo "🚀 Construyendo la infraestructura de EcoWarm (Iteración 5)..."

# El docker-compose ya sabe dónde está el Dockerfile. 
# Al ejecutar esto, Maven va a compilar el core, correr los tests TDD, y armar la imagen del engine.
docker compose -f docker/docker-compose.yml build

echo "✅ Pipeline completado. Imágenes listas para levantar."