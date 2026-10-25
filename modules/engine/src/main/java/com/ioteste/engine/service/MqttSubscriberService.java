package com.ioteste.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ioteste.engine.domain.MensajeTermostato;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

@Service
public class MqttSubscriberService implements MqttCallback {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private MqttClient client;

    public MqttSubscriberService(DataSource dataSource) {
        this.dataSource = dataSource;
        this.objectMapper = new ObjectMapper();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciarSuscripcion() {
        new Thread(() -> {
            boolean conectado = false;
            while (!conectado) {
                try {
                    System.out.println("Intentando conectar al broker MQTT: " + brokerUrl);
                    client = new MqttClient(brokerUrl, MqttClient.generateClientId(), new MemoryPersistence());

                    MqttConnectOptions options = new MqttConnectOptions();
                    options.setAutomaticReconnect(true);
                    options.setCleanSession(true);
                    options.setConnectionTimeout(10);

                    client.setCallback(this);
                    client.connect(options);

                    // Suscripción explícita
                    client.subscribe("ht-room1/status/temperature:0", 1);
                    client.subscribe("ht-room2/status/temperature:0", 1);

                    System.out.println("¡Conectado al broker MQTT y escuchando temperaturas!");
                    conectado = true;
                } catch (MqttException e) {
                    System.out.println("Broker MQTT no disponible. Reintentando en 5 segundos...");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }).start();
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) {
        try {
            String payload = new String(mqttMessage.getPayload());
            MensajeTermostato mensaje = objectMapper.readValue(payload, MensajeTermostato.class);

            try (Connection conn = dataSource.getConnection()) {
                // Solo necesitamos el ID de la habitación para guardar el histórico
                String queryHab = "SELECT id FROM habitacion WHERE topic_termostato = ?";
                String idHabitacion = null;

                try (PreparedStatement stmt = conn.prepareStatement(queryHab)) {
                    stmt.setString(1, topic);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            idHabitacion = rs.getString("id");
                        }
                    }
                }

                if (idHabitacion != null) {
                    String insertTemp = "INSERT INTO historico_temperatura (habitacion_id, fecha_hora, temperatura_c) VALUES (?, ?, ?)";
                    try (PreparedStatement stmtInsert = conn.prepareStatement(insertTemp)) {
                        stmtInsert.setString(1, idHabitacion);
                        stmtInsert.setTimestamp(2, new Timestamp((long) (mensaje.ts() * 1000)));
                        stmtInsert.setDouble(3, mensaje.tC());
                        stmtInsert.executeUpdate();
                    }
                    System.out.println("Termómetro MQTT -> Registrado: " + mensaje.tC() + "°C en " + idHabitacion);
                }
            }
        } catch (Exception e) {
            System.err.println("Error procesando payload del tópico " + topic + ": " + e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.err.println("Alerta: Se perdió la conexión con el broker MQTT.");
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {}
}