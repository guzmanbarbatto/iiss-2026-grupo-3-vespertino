#!/bin/bash
# ==============================================================================
# Script: send-temp.sh
# Descripción: Simula la publicación de un mensaje MQTT desde un dispositivo
#              Shelly H&T Gen 3. Envía datos de temperatura y humedad en formato
#              JSON al broker Mosquitto.
# ==============================================================================

echo "=========================================="
echo "    Simulando Shelly H&T Gen 3            "
echo "=========================================="
echo "Enviando telemetría al tópico: shelly/ht/room1/status"
echo "------------------------------------------"

# Definimos el mensaje JSON con datos simulados
PAYLOAD='{"temperature": 21.5, "humidity": 45.0, "battery": 100}'

echo "Payload a enviar: $PAYLOAD"

# Usamos docker exec para ejecutar mosquitto_pub desde dentro del contenedor.
# -h localhost            : Conecta al broker local dentro del contenedor.
# -t "shelly/ht/..."      : El tópico MQTT donde se publica el mensaje.
# -m "$PAYLOAD"           : El mensaje JSON que definimos arriba.

docker exec -it iot_mosquitto_broker mosquitto_pub -h localhost -t "shelly/ht/room1/status" -m "$PAYLOAD"

echo "------------------------------------------"
echo "✅ Mensaje publicado con éxito."