package com.ioteste.subscriber;

import org.eclipse.paho.client.mqttv3.*;
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

            System.out.println("Conectando al broker MQTT en: " + brokerUrl);
            mqttClient.connect(connOpts);
            System.out.println("¡Conectado exitosamente al broker!");

            // Definimos qué hacer cuando ocurren eventos asíncronos (callbacks)
            mqttClient.setCallback(new MqttCallback() {

                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("Conexión perdida con el broker. Causa: " + cause.getMessage());
                }

                /*
                 * Este método se dispara automáticamente cada vez que llega un mensaje
                 * a un tópico al que estamos suscritos.
                 */
                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    System.out.println("==========================================");
                    System.out.println("Tópico recibido : " + topic);

                    byte[] payload = message.getPayload();

                    System.out.println("Payload recibido: " + new String(payload));

                    try {
                        EventoTemperatura evento = deserializer.deserializar(payload);

                        System.out.println("ID habitación   : " + evento.getid());
                        System.out.println("Temperatura °C   : " + evento.gettC());
                        System.out.println("Temperatura °F   : " + evento.gettF());
                        System.out.println("Timestamp        : " + evento.getts());

                        Habitacion habitacion = repository.buscarPorId(evento.getid());

                        if (habitacion != null) {
                            System.out.println("Habitación encontrada:");
                            System.out.println("Termostato       : " + habitacion.getTermostato());
                            System.out.println("Switch           : " + habitacion.getSwitchId());
                            System.out.println("Temperatura objetivo: "
                                    + habitacion.getTemperaturaObjetivo());
                        } else {
                            System.out.println("No existe una habitación con ID "
                                    + evento.getid());
                        }
                    } catch(IOException e){
                        System.out.println("Error al deserializar el mensaje.");
                        System.out.println("El payload no corresponde a EventoTemperatura.");
                        System.out.println("Detalle: " + e.getMessage());
                    }

                    System.out.println("==========================================");
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
            System.out.println("Suscrito a todos los tópicos (#). Esperando mensajes...");

        } catch (MqttException me) {
            // Manejo de errores específicos del protocolo MQTT
            System.out.println("Error MQTT - Razón: " + me.getReasonCode());
            System.out.println("Mensaje: " + me.getMessage());
            me.printStackTrace();
        }
    }
}