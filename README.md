# Ing-Software-Grupo3
--------------------------------------------------------------
# IoTEste - Prototipo MQTT Base (Iteración 1)

Este repositorio contiene la primera iteración del producto exploratorio de domótica para IoTEste. El objetivo principal es establecer la infraestructura base contenerizada utilizando Docker, levantar un broker MQTT (Eclipse Mosquitto) y probar la comunicación mediante un suscriptor desarrollado en Java.

## 📁 Estructura del Repositorio

El repositorio está organizado de la siguiente manera para cumplir con los entregables de la Iteración 1:

- `/docs/vision.md`: Documento de Visión del Producto utilizando la plantilla de Moore.
- `/docker/docker-compose.yml`: Archivo de orquestación que define y levanta el broker Mosquitto y el contenedor del suscriptor Java en una misma red.
- `/scripts/`: Directorio con scripts en Bash para gestionar la infraestructura y simular la interacción con el sistema.
- `/src/subscriber/`: Código fuente del componente Java mínimo que actúa como suscriptor MQTT, junto con su respectivo `Dockerfile` para ser empaquetado.

## 🛠️ Descripción de los Scripts

Los scripts automatizan las tareas repetitivas del entorno de desarrollo:

- `up.sh`: Construye la imagen de Java (si hay cambios) y levanta toda la infraestructura en segundo plano usando `docker-compose up --build -d`.
- `stop.sh`: Detiene los contenedores en ejecución sin destruir los recursos (`docker-compose stop`).
- `down.sh`: Detiene y elimina los contenedores, redes y volúmenes creados (`docker-compose down`).
- `send-temp.sh`: Simula la publicación de un mensaje MQTT con formato JSON (emulando las capacidades de un dispositivo Shelly H&T Gen 3).
- `receive-temp.sh`: Script de utilidad por consola para suscribirse al broker y verificar la recepción de mensajes de forma independiente al suscriptor Java.

## 🚀 Cómo compilar y levantar el Suscriptor Java

Todo el proceso de compilación y ejecución ocurre dentro de Docker.

1. Abre tu terminal en la raíz del proyecto.
2. Ejecuta el script de inicio para levantar el broker y compilar/levantar la app Java simultáneamente:
   ```bash
   ./scripts/up.sh
