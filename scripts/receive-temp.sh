#!/bin/bash
# ==============================================================================
# Script: receive-temp.sh
# Descripción: Se suscribe al broker MQTT para verificar la recepción de
#              mensajes por consola (alternativo al suscriptor de Java).
# ==============================================================================

echo "=========================================="
echo "    Escuchando mensajes MQTT (Mosquitto)  "
echo "=========================================="
echo "Suscrito a todos los tópicos ('#'). Esperando datos de los Shelly..."
echo "Presiona Ctrl+C para detener la escucha."
echo "------------------------------------------"

# Usamos docker exec para correr el cliente mosquitto_sub directamente 
# dentro del contenedor del broker (iot_mosquitto_broker).
# -h localhost : Se conecta al propio broker.
# -t "#"       : Se suscribe a todos los tópicos disponibles.
# -v           : Modo detallado (verbose), imprime el nombre del tópico junto al mensaje JSON.

docker exec iot_mosquitto_broker mosquitto_sub -h localhost -p 1883 -t "#" -v