package com.ioteste.subscriber;

import org.eclipse.paho.client.mqttv3.*;
import org.json.JSONObject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

public class Subscriber {
    public static void main(String[] args) {
        String brokerUrl = "tcp://mosquitto:1883";
        String clientId = "JavaSubscriber_v1";

        // Credenciales y URL de la DB en Docker
        String dbUrl = "jdbc:postgresql://db:5432/ecowarm";
        String dbUser = "ecowarm_user";
        String dbPass = "ecowarmpass";

        try {
            MqttClient mqttClient = new MqttClient(brokerUrl, clientId);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);

            Logger.info("Conectando al broker MQTT en: {}", brokerUrl);
            mqttClient.connect(connOpts);
            Logger.info("¡Conectado exitosamente al broker!");

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    Logger.error(cause, "Conexión perdida con el broker");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String payload = new String(message.getPayload());
                    System.out.println("==========================================");
                    System.out.println("Tópico recibido : " + topic);
                    System.out.println("Payload JSON    : " + payload);

                    try {
                        // 1. Parsear el JSON
                        JSONObject json = new JSONObject(payload);
                        int id = json.getInt("id");
                        double tC = json.getDouble("tC");
                        double ts = json.getDouble("ts");

                        // 2. Convertir epoch a Timestamp (segundos a milisegundos)
                        long epochMillis = (long) (ts * 1000);
                        Timestamp timestamp = new Timestamp(epochMillis);

                        // 3. Persistir en la base de datos
                        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                            String sql = "INSERT INTO historico_temperatura (habitacion_id, fecha_hora, temperatura_c) VALUES (?, ?, ?)";
                            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                                pstmt.setInt(1, id);
                                pstmt.setTimestamp(2, timestamp);
                                pstmt.setDouble(3, tC);
                                pstmt.executeUpdate();
                                System.out.println("-> Registro de " + tC + "°C persistido en PostgreSQL.");
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error al procesar/persistir el mensaje: " + e.getMessage());
                    }
                    System.out.println("==========================================");
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });

            mqttClient.subscribe("#");
            Logger.info("Suscrito a todos los tópicos (#). Esperando mensajes...");

        } catch (MqttException me) {
            me.printStackTrace();
        }
    }
}