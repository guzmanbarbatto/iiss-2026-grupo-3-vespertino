package com.ioteste.engine.service;

import com.ioteste.engine.domain.AltaControlador;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SimuladorRegistroService {

    @Value("${simulador.url}")
    private String simuladorUrl;

    @Value("${app.api.key}")
    private String miApiKey;
    
    // Si aún no tenemos la clave, asume vacío
    @Value("${simulador.api.key:}") 
    private String simuladorApiKey;

    // Estos son nuestros datos de identificación en la red de Docker
    private final String baseUrl = "http://localhost:8080";
    private final String etiqueta = "control-grupo-3";

    @EventListener(ApplicationReadyEvent.class)
    public void registrarEnSegundoPlano() {
        // Lanzamos el registro en un hilo aparte para no bloquear el arranque de nuestra API
        new Thread(this::intentarRegistro).start();
    }

    private void intentarRegistro() {
        RestTemplate restTemplate = new RestTemplate();
        String endpoint = simuladorUrl + "/controladores";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", simuladorApiKey);
        headers.set("Content-Type", "application/json");
        
        // Le decimos al simulador cómo nos llamamos, dónde estamos y qué clave debe usar para hablarnos
        AltaControlador payload = new AltaControlador(etiqueta, baseUrl, miApiKey);
        HttpEntity<AltaControlador> request = new HttpEntity<>(payload, headers);

        boolean registrado = false;
        while (!registrado) {
            try {
                System.out.println("Intentando registrar controlador en el simulador: " + endpoint);
                restTemplate.postForEntity(endpoint, request, String.class);
                System.out.println("¡Registro exitoso! El simulador ya nos conoce.");
                registrado = true;
            } catch (Exception e) {
                System.out.println("El simulador aún no responde. Reintentando en 5 segundos...");
                try {
                    Thread.sleep(5000); // Pausa de 5 segundos
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}