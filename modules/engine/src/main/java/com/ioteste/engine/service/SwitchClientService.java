package com.ioteste.engine.service;

import com.ioteste.engine.domain.ComandoSwitch;
import com.ioteste.engine.domain.EstadoSwitchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SwitchClientService {

    @Value("${simulador.api.key:}")
    private String simuladorApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public void comandarSwitch(String urlSwitch, String idSwitch, String accion) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", simuladorApiKey);
        headers.set("Content-Type", "application/json");

        // 1. FIX PUNTO §4: Consultar el estado actual del switch antes de comandarlo
        String urlEstado = urlSwitch + "/switches/" + idSwitch;
        try {
            HttpEntity<Void> getEntity = new HttpEntity<>(headers);
            restTemplate.exchange(urlEstado, HttpMethod.GET, getEntity, String.class);
        } catch (Exception e) {
            System.err.println("Fallo al consultar el switch " + idSwitch + " - " + e.getMessage());
        }

        // 2. PUNTO §3: Enviar el comando ON / OFF al switch
        String urlComandos = urlSwitch + "/switches/" + idSwitch + "/comandos";
        ComandoSwitch body = new ComandoSwitch(accion);
        HttpEntity<ComandoSwitch> postEntity = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(urlComandos, HttpMethod.POST, postEntity, String.class);
            System.out.println("Comando [" + accion + "] enviado exitosamente al switch: " + idSwitch);
        } catch (Exception e) {
            System.err.println("Fallo al comandar el switch " + idSwitch + " - " + e.getMessage());
        }
    }

    public EstadoSwitchResponse consultarEstado(String urlSwitch, String idSwitch) {

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", simuladorApiKey);

        String url = urlSwitch + "/switches/" + idSwitch;
        HttpEntity<Void> request = new HttpEntity<>(headers);

        return restTemplate.exchange(url, HttpMethod.GET, request, EstadoSwitchResponse.class).getBody();
    }

}