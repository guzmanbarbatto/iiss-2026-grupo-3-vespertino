package com.ioteste.api.service;

import com.ioteste.api.repository.RoomRepository;
import org.eclipse.paho.client.mqttv3.*;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import jakarta.annotation.PostConstruct;
import java.util.Map;

@Service
public class ThermostatService {
    @Value("${mqtt.broker.url}") private String brokerUrl;
    @Value("${api.auth.token}") private String token;
    
    private final RoomRepository roomRepository;
    private boolean isRunning = false;

    public ThermostatService(RoomRepository roomRepository) { this.roomRepository = roomRepository; }

    public void start() { this.isRunning = true; System.out.println("Termostato: INICIADO");}
    public void stop() { this.isRunning = false; System.out.println("Termostato: DETENIDO");}

    @PostConstruct
    public void init() {
        try {
            MqttClient client = new MqttClient(brokerUrl, "ApiTermostato");
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            
            client.setCallback(new MqttCallback() {
                @Override public void connectionLost(Throwable cause) {}
                @Override public void deliveryComplete(IMqttDeliveryToken token) {}
                @Override public void messageArrived(String topic, MqttMessage message) {
                    if (!isRunning) return; // Lógica detenida
                    try {
                        JSONObject json = new JSONObject(new String(message.getPayload()));
                        int roomId = json.getInt("id");
                        double tempActual = json.getDouble("tC");

                        roomRepository.findById(roomId).ifPresent(room -> {
                            String accion = tempActual > room.getTemperaturaObjetivo() ? "OFF" : "ON";
                            enviarComandoStub(room.getSwitchId(), accion);
                        });
                    } catch (Exception e) { e.printStackTrace(); }
                }
            });
            client.connect(options);
            client.subscribe("#");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void enviarComandoStub(String switchId, String accion) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            HttpEntity<Map<String, String>> req = new HttpEntity<>(Map.of("action", accion), headers);
            // Llama al endpoint STUB de la propia API
            restTemplate.postForEntity("http://localhost:8080/api/stub/switch/" + switchId, req, String.class);
        } catch(Exception e) { System.err.println("Fallo al llamar stub: " + e.getMessage()); }
    }
}