package com.ioteste.subscriber;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MqttMessageHandler implements MqttCallback {
    private static final Logger LOGGER = Logger.getLogger(MqttMessageHandler.class.getName());
    private final DatabaseRepository dbRepository;

    public MqttMessageHandler(DatabaseRepository dbRepository) {
        this.dbRepository = dbRepository;
    }

    @Override
    public void connectionLost(Throwable cause) {
        LOGGER.log(Level.SEVERE, "Conexión interrumpida con el Broker MQTT.", cause);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            JSONObject json = new JSONObject(payload);
            
            int id = json.getInt("id");
            double tC = json.getDouble("tC");
            
            long ts = json.getLong("ts"); 
            long epochMillis = ts * 1000L; 

            dbRepository.insertarHistorico(id, epochMillis, tC);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error procesando el mensaje MQTT", e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // No requerido para suscripciones
    }
}