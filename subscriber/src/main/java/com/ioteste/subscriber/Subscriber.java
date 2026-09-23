package com.ioteste.subscriber;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Subscriber {
    private static final Logger LOGGER = Logger.getLogger(Subscriber.class.getName());

    public static void main(String[] args) {
        String brokerUrl = System.getenv("MQTT_BROKER_URL") != null ? System.getenv("MQTT_BROKER_URL") : "tcp://localhost:1883";
        String clientId = "JavaSubscriber_v2";

        String dbUrl = System.getenv("DB_URL") != null ? System.getenv("DB_URL") : "jdbc:postgresql://localhost:5432/ecowarm";
        String dbUser = System.getenv("POSTGRES_USER") != null ? System.getenv("POSTGRES_USER") : "ecowarm_user";
        String dbPass = System.getenv("POSTGRES_PASSWORD") != null ? System.getenv("POSTGRES_PASSWORD") : "ecowarmpass";

        try {
            // 1. Inicializamos la base de datos
            DatabaseRepository dbRepository = new DatabaseRepository(dbUrl, dbUser, dbPass);
            
            // 2. Pasamos el repo al controlador de MQTT
            MqttMessageHandler messageHandler = new MqttMessageHandler(dbRepository);

            // 3. Levantamos el cliente delegando las tareas a la clase aislada
            MqttClient mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);

            mqttClient.setCallback(messageHandler);

            LOGGER.info("Conectando al broker MQTT: " + brokerUrl);
            mqttClient.connect(connOpts);
            LOGGER.info("Conexión establecida exitosamente.");

            mqttClient.subscribe("#");
            LOGGER.info("Escuchando mensajes entrantes...");

        } catch (MqttException me) {
            LOGGER.log(Level.SEVERE, "Fallo al inicializar el suscriptor", me);
        }
    }
}