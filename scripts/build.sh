#!/bin/bash
echo "Compilando el sistema mediante Docker..."
docker compose -f docker/docker-compose.yml build
