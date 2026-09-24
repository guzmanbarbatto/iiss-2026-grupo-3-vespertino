# Ing-Software-Grupo3: IoTEste / EcoWarm - API REST y Termostato (Iteración 3)

Este repositorio contiene la tercera iteración del prototipo exploratorio de domótica EcoWarm para IoTEste[cite: 3, 4]. El objetivo principal de esta etapa es exponer una API REST completa (Nivel 2 de Richardson) para el CRUD de habitaciones y el envío de comandos/tareas, proteger el acceso mediante autenticación Bearer Token, e integrar la lógica de control del termostato junto a un stub del switch.

## Gestión del Proyecto
* **Tablero Jira:** https://cure-software.atlassian.net/jira/software/projects/INGSOF/boards/1[cite: 4]

## Estructura del Repositorio
* `/docs/api/openapi.yaml`: Definición técnica OpenAPI 3.0 de la API REST[cite: 3].
* `/docs/vision.md`: Documento de Visión del Producto (Actualizado a Iteración 3)[cite: 4, 5].
* `/docs/metodologia.md`: Declaración y gestión metodológica en Scrumban[cite: 4, 40].
* `/docs/producto/`: Escenarios, Historias de Usuario (HU-1 a HU-5) y Características de EcoWarm[cite: 4, 42].
* `/docker/docker-compose.yml`: Orquestación de Mosquitto Broker, PostgreSQL, Suscriptor, Generador y API REST Spring Boot.
* `/scripts/`:
  * `build.sh`: Compila los módulos Maven aislados mediante Docker[cite: 3, 29].
  * `up.sh`: Construye e inicia todos los servicios en segundo plano[cite: 4, 27].
  * `down.sh` / `stop.sh`: Detención y limpieza de contenedores y redes[cite: 4, 25, 36].
  * `test_api_curl.sh`: Suite de validación E2E para la API REST mediante cURL con autenticación[cite: 3].
* `/api/`: Módulo Spring Boot con la API REST, seguridad Bearer, comandos y motor del termostato[cite: 3, 24, 26].

## Despliegue y Ejecución

### 1. Compilación
Para empaquetar los módulos Maven dentro del entorno aislado de Docker[cite: 3, 4]:
```bash
./scripts/build.sh