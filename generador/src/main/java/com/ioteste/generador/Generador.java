package com.ioteste.generador;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.time.Instant;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Generador {
    
    private static final Logger LOGGER = Logger.getLogger(Generador.class.getName());

    public static void main(String[] args) {
        // Resolución del broker vía variable de entorno o localhost por defecto
        String broker = System.getenv("MQTT_BROKER_URL") != null ? System.getenv("MQTT_BROKER_URL") : "tcp://localhost:1883";
        String clientId = "MockShellyHT_01";
        String topic = "ht-sim-rooml/status/temperature:0"; 

        try {
            MqttClient client = new MqttClient(broker, clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            
            LOGGER.info("Conectando al Broker MQTT en: " + broker);
            client.connect(options);
            LOGGER.info("Conexión establecida.");

            // Pool de hilos para ejecución asíncrona
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

            scheduler.scheduleAtFixedRate(() -> {
                try {
                    double tC = 21.0 + (Math.random() * 2); 
                    double tF = (tC * 9/5) + 32;
                    
                    // Corrección: Uso de Long estricto para el timestamp (Epoch en segundos)
                    long ts = Instant.now().getEpochSecond(); 

                    // Formateo del payload JSON
                    String payload = String.format(java.util.Locale.US, 
                        "{\"id\":0,\"tC\":%.1f, \"tF\":%.1f,\"ts\":%d}", 
                        tC, tF, ts);
                    
                    MqttMessage message = new MqttMessage(payload.getBytes());
                    message.setQos(1); 
                    
                    client.publish(topic, message);
                    LOGGER.info("Telemetría enviada -> " + payload);
                    
                } catch (MqttException e) {
                    LOGGER.log(Level.SEVERE, "Error al publicar el mensaje", e);
                }
            }, 0, 5, TimeUnit.SECONDS); // Ejecución cada 5 segundos

            // Cierre seguro al detener la aplicación
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Apagando generador...");
                scheduler.shutdown();
                try {
                    client.disconnect();
                } catch (MqttException e) {
                    LOGGER.log(Level.SEVERE, "Error al desconectar del broker", e);
                }
            }));

        } catch (MqttException e) {
            LOGGER.log(Level.SEVERE, "Fallo al iniciar el cliente MQTT.", e);
        }
    }
}