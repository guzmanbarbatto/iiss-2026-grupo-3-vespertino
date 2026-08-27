# Ing-Software-Grupo3: IoTEste / EcoWarm - Prototipo MQTT (Iteración 2)

Este repositorio contiene la segunda iteración del producto exploratorio de domótica EcoWarm para IoTEste. El objetivo principal de esta etapa es consolidar la definición del alcance del producto, migrar la compilación a Maven, implementar la persistencia de datos de las habitaciones e integrar un generador de eventos automatizado mediante Docker.

## Gestión del Proyecto
* **Tablero Jira:** https://cure-software.atlassian.net/jira/software/projects/INGSOF/boards/1

## Estructura del Repositorio
El repositorio crece de forma incremental y está organizado de la siguiente manera:
* `/docs/vision.md`: Documento de Visión del Producto (versión 2).
* `/docs/metodologia.md`: Declaración y justificación de la metodología ágil adoptada (Scrumban).
* `/docs/producto/`: Documentación de diseño con los Escenarios, Historias de Usuario y Características de EcoWarm.
* `/docker/docker-compose.yml`: Archivo de orquestación que define y levanta el broker Mosquitto, el Generador de eventos (Java) y el Receptor/Suscriptor (Java) en una misma red.
* `/scripts/`: Directorio con scripts en Bash para gestionar la infraestructura.
* `/src/`: Código fuente de los dos clientes Java (Generador y Receptor), ahora estructurados como módulos Maven.
* `/.github/workflows/maven.yml`: Pipeline de GitHub Actions para la Integración Continua (CI).

## Descripción de los Scripts
Los scripts automatizan las tareas repetitivas del entorno de desarrollo sin depender de herramientas locales:
* `build.sh`: Compila todo el sistema y empaqueta los módulos Maven estrictamente mediante Docker (no requiere Java ni Maven instalados en el host).
* `up.sh`: Construye las imágenes y levanta toda la infraestructura en segundo plano (`docker compose up --build -d`). Esto inicia automáticamente el Broker, el Generador de eventos y el Receptor.
* `stop.sh`: Detiene los contenedores en ejecución sin destruir los recursos.
* `down.sh`: Detiene y elimina los contenedores, redes y volúmenes creados.

## Cómo levantar (y bajar) el sistema
Todo el proceso ocurre dentro de Docker. Abre tu terminal en la raíz del proyecto y ejecuta:

1. **Para compilar el proyecto:**
   ```bash
   ./scripts/build.sh