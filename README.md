# Ing-Software-Grupo3: IoTEste - Prototipo MQTT Base (Iteración 1)

Este repositorio contiene la primera iteración del producto exploratorio de domótica para IoTEste. El objetivo principal es establecer la infraestructura base contenerizada utilizando Docker, levantar un broker MQTT (Eclipse Mosquitto) y probar la comunicación mediante un suscriptor desarrollado en Java.

## 🔗 Gestión del Proyecto
* **Tablero Jira:** https://cure-software.atlassian.net/jira/software/projects/INGSOF/boards/1

## 📁 Estructura del Repositorio
El repositorio está organizado de la siguiente manera para cumplir con los entregables de la Iteración 1:
* `/docs/vision.md`: Documento de Visión del Producto utilizando la plantilla de Moore y análisis de capacidades (Shelly Pro 1PM y H&T Gen 3).
* `/docker/docker-compose.yml`: Archivo de orquestación que define y levanta el broker Mosquitto y el contenedor del suscriptor Java en una misma red.
* `/scripts/`: Directorio con scripts en Bash para gestionar la infraestructura y simular la interacción con el sistema.
* `/src/subscriber/`: Código fuente del componente Java mínimo que actúa como suscriptor MQTT, junto con su respectivo Dockerfile para ser empaquetado.

## 🛠️ Descripción de los Scripts
Los scripts automatizan las tareas repetitivas del entorno de desarrollo sin depender de herramientas locales:
* `build.sh`: Compila el código del suscriptor Java estrictamente mediante Docker.
* `up.sh`: Construye la imagen de Java (si hay cambios) y levanta toda la infraestructura en segundo plano (`docker compose up --build -d`).
* `stop.sh`: Detiene los contenedores en ejecución sin destruir los recursos.
* `down.sh`: Detiene y elimina los contenedores, redes y volúmenes creados.
* `send-temp.sh`: Simula la publicación de un mensaje MQTT con formato JSON (emulando las capacidades de un dispositivo Shelly H&T Gen 3).
* `receive-temp.sh`: Script de utilidad por consola para suscribirse al broker y verificar la recepción de mensajes.

## 🚀 Cómo levantar (y bajar) el sistema
Todo el proceso ocurre dentro de Docker. Abre tu terminal en la raíz del proyecto y ejecuta:

1. **Para levantar:**
   ```bash
   ./scripts/up.sh
