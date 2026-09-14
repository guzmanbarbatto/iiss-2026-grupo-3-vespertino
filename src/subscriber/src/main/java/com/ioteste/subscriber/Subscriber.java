package com.ioteste.subscriber;

// Importación de las primitivas de la API OASIS MQTT para la comunicación asíncrona orientada a eventos.
import org.eclipse.paho.client.mqttv3.*;
// Inclusión de la biblioteca de parsing para la deserialización de gramáticas JSON.
import org.json.JSONObject;
// Primitivas de la API Java Database Connectivity (JDBC) para la manipulación de datos relacionales.
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
// Framework de auditoría nativo de Java para la segregación de logs y mitigación de cuellos de botella por I/O sincrónico.
import java.util.logging.Logger;
import java.util.logging.Level;

public class Subscriber {
    
    // Declaración del motor de auditoría estático, final e inmutable, acoplado al contexto de la clase actual.
    private static final Logger LOGGER = Logger.getLogger(Subscriber.class.getName());

    public static void main(String[] args) {
        // Interrogación del entorno de ejecución para la resolución dinámica de variables topológicas inyectadas por el orquestador.
        String brokerUrl = System.getenv("MQTT_BROKER_URL") != null ? System.getenv("MQTT_BROKER_URL") : "tcp://localhost:1883";
        String clientId = "JavaSubscriber_v2";

        // Parámetros de conectividad determinista hacia el clúster relacional (RDBMS).
        String dbUrl = System.getenv("DB_URL") != null ? System.getenv("DB_URL") : "jdbc:postgresql://localhost:5432/ecowarm";
        String dbUser = System.getenv("POSTGRES_USER") != null ? System.getenv("POSTGRES_USER") : "ecowarm_user";
        String dbPass = System.getenv("POSTGRES_PASSWORD") != null ? System.getenv("POSTGRES_PASSWORD") : "ecowarmpass";

        try {
            // Instanciación del cliente MQTT delegando la persistencia de sesión a la memoria volátil (Heap).
            MqttClient mqttClient = new MqttClient(brokerUrl, clientId, new org.eclipse.paho.client.mqttv3.persist.MemoryPersistence());
            MqttConnectOptions connOpts = new MqttConnectOptions();
            // Activación del flag Clean Session para garantizar la purga del estado transaccional al finalizar la conexión.
            connOpts.setCleanSession(true);

            LOGGER.info("Iniciando handshake en la capa de transporte TCP/IP hacia el broker MQTT en: " + brokerUrl);
            mqttClient.connect(connOpts);
            LOGGER.info("Conexión socket bidireccional establecida exitosamente.");

            // Inyección de dependencia (Callback) para el manejo de interrupciones asíncronas en el hilo receptor (Listener Thread).
            mqttClient.setCallback(new MqttCallback() {
                
                @Override
                public void connectionLost(Throwable cause) {
                    LOGGER.log(Level.SEVERE, "Pérdida de latido (Heartbeat). Conexión en capa de transporte interrumpida.", cause);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    // Extracción del vector de bytes y decodificación a cadena alfanumérica.
                    String payload = new String(message.getPayload());
                    
                    try {
                        // Deserialización del Data Transfer Object (DTO) proveniente de la capa de aplicación.
                        JSONObject json = new JSONObject(payload);
                        int id = json.getInt("id");
                        double tC = json.getDouble("tC");
                        double ts = json.getDouble("ts");

                        // Transformación cronológica: conversión de escalar UNIX Epoch a estructura temporal milimétrica (Timestamp).
                        long epochMillis = (long) (ts * 1000.0);
                        Timestamp timestamp = new Timestamp(epochMillis);

                        // Establecimiento del túnel JDBC implementando 'Try-with-resources' para la mitigación automática de fugas de memoria (Memory Leaks).
                        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                            // Definición del Lenguaje de Manipulación de Datos (DML) parametrizado para evadir vulnerabilidades de inyección SQL (SQLi).
                            String sql = "INSERT INTO historico_temperatura (habitacion_id, fecha_hora, temperatura_c) VALUES (?, ?, ?)";
                            
                            // Compilación del plan de ejecución en el motor de base de datos.
                            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                                pstmt.setInt(1, id);
                                pstmt.setTimestamp(2, timestamp);
                                pstmt.setDouble(3, tC);
                                // Ejecución sincrónica de la transacción garantizando atomicidad, consistencia, aislamiento y durabilidad (ACID).
                                pstmt.executeUpdate();
                                
                                LOGGER.info(String.format("Integridad referencial confirmada. Transacción exitosa [Habitación %d] -> %s°C persistido en el esquema relacional.", id, tC));
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Anomalía crítica interceptada durante el pipeline de deserialización o persistencia relacional.", e);
                    }
                }

                @Override
                // Implementación obligatoria exigida por el contrato estructural de la interfaz MqttCallback. 
                public void deliveryComplete(IMqttDeliveryToken token) { 
                    // Retrollamada pasiva exclusiva para topologías de publicación (QoS > 0).
                }
            });

            // Suscripción topológica al árbol de enrutamiento implementando el comodín multinivel (#) para la agregación total de telemetría.
            mqttClient.subscribe("#");
            LOGGER.info("Suscripción topológica propagada. Demultiplexor asíncrono en estado de escucha (Listening).");

        } catch (MqttException me) {
            LOGGER.log(Level.SEVERE, "Excepción crítica detectada en el aprovisionamiento del clúster de mensajería OASIS MQTT", me);
        }
    }
}