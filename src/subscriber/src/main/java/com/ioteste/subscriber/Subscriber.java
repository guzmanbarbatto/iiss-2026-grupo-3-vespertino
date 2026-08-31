package com.ioteste.subscriber;

import org.eclipse.paho.client.mqttv3.*;
import org.tinylog.Logger;
import com.ioteste.subscriber.Domain.EventoTemperatura;
import com.ioteste.subscriber.service.EventoTemperaturaDeserializer;
import com.ioteste.subscriber.Domain.Habitacion;
import com.ioteste.subscriber.repository.HabitacionRepository;

import java.io.IOException;

public class Subscriber {
    public static void main(String[] args) {
        /*
         * URL del broker MQTT.
         * No usamos "localhost" porque estamos dentro de la red de Docker.
         * Usamos "mosquitto" porque es el nombre del servicio definido en el docker-compose.yml.
         */
        HabitacionRepository repository = new HabitacionRepository();
        repository.inicializar();

        EventoTemperaturaDeserializer deserializer =
                new EventoTemperaturaDeserializer();

        String brokerUrl = "tcp://mosquitto:1883";

        // Identificador único para este cliente en el broker MQTT
        String clientId = "JavaSubscriber_v1";

        try {
            // Inicializamos el cliente MQTT con la URL y el ID
            MqttClient mqttClient = new MqttClient(brokerUrl, clientId);

            // Configuramos las opciones de conexión
            MqttConnectOptions connOpts = new MqttConnectOptions();
            // cleanSession(true) significa que no queremos que el broker guarde mensajes
            // antiguos si nos desconectamos. Es un suscriptor en tiempo real.
            connOpts.setCleanSession(true);

            Logger.info("Conectando al broker MQTT en: {}", brokerUrl);
            mqttClient.connect(connOpts);
            Logger.info("¡Conectado exitosamente al broker!");

            // Definimos qué hacer cuando ocurren eventos asíncronos (callbacks)
            mqttClient.setCallback(new MqttCallback() {

                @Override
                public void connectionLost(Throwable cause) {
                    Logger.error(cause, "Conexión perdida con el broker");
                }

                /*
                 * Este método se dispara automáticamente cada vez que llega un mensaje
                 * a un tópico al que estamos suscritos.
                 */
                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    Logger.info("Tópico recibido: {}", topic);

                    byte[] payload = message.getPayload();

                    Logger.info("Payload recibido: {}", new String(payload));

                    try {
                        EventoTemperatura evento = deserializer.deserializar(payload);

                        Logger.info("ID habitación: {}", evento.getid());
                        Logger.info("Temperatura °C: {}", evento.gettC());
                        Logger.info("Temperatura °F: {}", evento.gettF());
                        Logger.info("Timestamp: {}", evento.getts());

                        Habitacion habitacion = repository.buscarPorId(evento.getid());

                        if (habitacion != null) {
                            Logger.info(
                                    "Habitación encontrada - ID: {}, Termostato: {}, Switch: {}, Temperatura objetivo: {}",
                                    habitacion.getId(),
                                    habitacion.getTermostato(),
                                    habitacion.getSwitchId(),
                                    habitacion.getTemperaturaObjetivo()
                            );
                        } else {
                            Logger.warn(
                                    "No existe una habitación con ID {}",
                                    evento.getid()
                            );
                        }
                    } catch(IOException e){
                        Logger.error(
                                e,
                                "Error al deserializar el mensaje. El payload no corresponde a EventoTemperatura"
                        );
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Este método se usa cuando publicamos mensajes,
                    // como somos un suscriptor puro, lo dejamos vacío.
                }
            });

            /*
             * Nos suscribimos al broker.
             * El símbolo "#" es un comodín (wildcard) en MQTT que significa "todos los tópicos".
             * En iteraciones futuras se puede ajustar a "shelly/#" para escuchar solo dispositivos.
             */
            mqttClient.subscribe("#");
            Logger.info("Suscrito a todos los tópicos (#). Esperando mensajes...");

        } catch (MqttException me) {
            // Manejo de errores específicos del protocolo MQTT
            Logger.error(
                    me,
                    "Error MQTT - Razón: {} - Mensaje: {}",
                    me.getReasonCode(),
                    me.getMessage()
            );
        }
    }
}