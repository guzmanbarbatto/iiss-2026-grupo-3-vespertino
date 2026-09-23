package com.ioteste.api.Service;

import com.ioteste.api.Domain.AccionSwitch; 
import com.ioteste.api.Domain.Habitacion;
import com.ioteste.api.Repository.HabitacionRepository;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class TermostatoService {

    private static final Logger LOGGER = Logger.getLogger(TermostatoService.class.getName());
    
    private final HabitacionRepository habitacionRepository;
    private final RestTemplate restTemplate;
    private MqttClient mqttClient;

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${stub.switch.url}")
    private String stubUrlTemplate;

    public TermostatoService(HabitacionRepository habitacionRepository) {
        this.habitacionRepository = habitacionRepository;
        this.restTemplate = new RestTemplate(); 
    }

    @PostConstruct
    public void iniciarSuscripcion() {
        try {
            mqttClient = new MqttClient(brokerUrl, "SpringBoot_Termostato", new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    LOGGER.warning("Se perdió la conexión con el Broker MQTT.");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    procesarLectura(new String(message.getPayload()));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            mqttClient.connect(options);
            mqttClient.subscribe("#"); 
            LOGGER.info("Servicio Termostato suscrito exitosamente a MQTT.");

        } catch (MqttException e) {
            LOGGER.log(Level.SEVERE, "Fallo al iniciar suscripción MQTT en el Controlador", e);
        }
    }

    private void procesarLectura(String payload) {
        try {
            JSONObject json = new JSONObject(payload);
            int idHabitacion = json.getInt("id");
            double temperaturaMedida = json.getDouble("tC");

            Optional<Habitacion> habitacionOpt = habitacionRepository.findById(idHabitacion);
            
            if (habitacionOpt.isPresent()) {
                Habitacion hab = habitacionOpt.get();
                double tempEsperada = hab.getTemperaturaEsperada(); 

                String orden = null;
                if (temperaturaMedida > tempEsperada) {
                    orden = "OFF"; 
                } else if (temperaturaMedida < tempEsperada) {
                    orden = "ON"; 
                }

                if (orden != null) {
                    ejecutarAccion(hab.getIdSwitch(), orden);
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error procesando lógica de control", e);
        }
    }

    private void ejecutarAccion(String idSwitch, String orden) {
        try {
            String urlDestino = stubUrlTemplate.replace("{idSwitch}", idSwitch);
            
            AccionSwitch requestBody = new AccionSwitch();
            requestBody.setAccion(orden);
            
            restTemplate.postForObject(urlDestino, requestBody, String.class);
            
            LOGGER.info(String.format("Callout REST exitoso: Orden '%s' enviada al switch %s", orden, idSwitch));
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fallo en la integración HTTP hacia el Stub", e);
        }
    }

    @PreDestroy
    public void apagar() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } catch (MqttException e) {
            LOGGER.severe("Error cerrando cliente MQTT.");
        }
    }
}