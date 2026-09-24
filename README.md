# Ing-Software-Grupo3: IoTEste / EcoWarm - Prototipo MQTT (Iteración 3)

Este repositorio contiene la tercera iteración del producto exploratorio de domótica EcoWarm para IoTEste. El objetivo principal de esta etapa es la construcción de una API REST utilizando Spring Boot (Java 25), la implementación de un modelo de persistencia relacional con PostgreSQL, la integración de seguridad mediante Autenticación Bearer y el acoplamiento de la lógica asíncrona del termostato vía MQTT.

## Gestión del Proyecto
* **Tablero Jira:** https://cure-software.atlassian.net/jira/software/projects/INGSOF/boards/1

## Estructura del Repositorio
El repositorio crece de forma incremental y está organizado bajo una estructura multi-módulo de Maven:

* `/docs/vision.md: Documento de Visión del Producto (versión actualizada).
* `/docs/metodologia.md: Declaración y justificación de la metodología ágil adoptada (Scrumban).
* `/docs/producto/: Documentación de diseño con los Escenarios, Historias de Usuario y Características de EcoWarm.
* `/docker/docker-compose.yml: Archivo de orquestación que define y levanta el ecosistema completo en una misma red: Broker Mosquitto, Base de Datos PostgreSQL, API Spring     Boot, Generador de eventos y Receptor/Suscriptor.
* `/scripts/: Directorio con scripts en Bash para gestionar la infraestructura y realizar pruebas.
* `/api/, /generador/, /subscriber/: Código fuente de los microservicios y clientes Java, estructurados como submódulos Maven independientes que heredan de un POM padre.
* `/.github/workflows/maven.yml: Pipeline de GitHub Actions para la Integración Continua (CI).

## Descripción de los Scripts

Los scripts automatizan las tareas repetitivas del entorno de desarrollo sin depender de herramientas locales:

* `build.sh: Compila todo el ecosistema y empaqueta los módulos Maven estrictamente mediante una etapa constructora en Docker (Java 25). No requiere Java ni Maven           instalados en el host.
* `up.sh: Construye las imágenes y levanta toda la infraestructura en segundo plano (docker-compose up -d --build). Esto inicia automáticamente la Base de Datos, el Broker, la API y los clientes MQTT.
* `test_api_curl.sh: Script de validación E2E (End-to-End) que ejecuta peticiones HTTP automáticas contra el contenedor de la API para probar el CRUD de habitaciones, la actualización de temperaturas y los comandos de los switches.
* `stop.sh: Detiene los contenedores en ejecución sin destruir los recursos.
* `down.sh: Detiene y elimina los contenedores, redes y volúmenes creados.

## Cómo levantar (y bajar) el sistema
Todo el proceso ocurre dentro de Docker. Abre tu terminal en la raíz del proyecto y ejecuta:

1. **Para compilar el proyecto:**
   ```bash
   ./scripts/build.sh

2. **Para levantar la infraestructura:**
   ```bash
   ./scripts/up.sh

3. **Para validar la API Rest:**
   ```bash
   ./scripts/test_api_curl.sh      
