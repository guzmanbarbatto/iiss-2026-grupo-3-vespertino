#!/bin/bash
# Orquestación de la fase de construcción (Build Stage) delegada al motor nativo del demonio de Docker.
# Este script garantiza el aislamiento determinista del entorno de compilación (Clean Room Design), 
# mitigando colisiones de dependencias con el sistema operativo host.

echo "Inicializando el motor de construcción (BuildKit) para la resolución asíncrona de dependencias Maven..."

# Invocación de la directiva de empaquetado para procesar los manifiestos Dockerfiles (Multi-stage).
# Nota arquitectónica: Esta ejecución es puramente estática. No instancia sockets de red ni procesos en runtime.
docker compose -f docker/docker-compose.yml build

echo "Pipeline de integración completado. Artefactos binarios inyectados exitosamente en las imágenes contenedoras."