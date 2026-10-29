package com.ioteste.mcp.client;

import com.ioteste.mcp.client.dto.SimuladorDtos.ComandoSwitch;
import com.ioteste.mcp.client.dto.SimuladorDtos.EstadoSwitch;
import com.ioteste.mcp.client.dto.SimuladorDtos.RespuestaComando;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente de los switches del simulador ({@code simulador.openapi-v2.yaml}).
 *
 * <p>Igual que el engine, usa siempre {@code SIMULADOR_URL} como base para los switches.
 */
@Component
public class SimuladorClient {

    private final RestClient restClient;

    public SimuladorClient(@Qualifier("simuladorRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** {@code GET /switches/{idSwitch}}. */
    public EstadoSwitch consultarSwitch(String idSwitch) {
        return ejecutar(idSwitch, "consultar", () -> restClient.get()
                .uri("/switches/{id}", idSwitch)
                .retrieve()
                .body(EstadoSwitch.class));
    }

    /** {@code POST /switches/{idSwitch}/comandos} con {@code accion} ON u OFF. */
    public RespuestaComando comandarSwitch(String idSwitch, boolean encender) {
        ComandoSwitch comando = new ComandoSwitch(encender ? "ON" : "OFF");
        return ejecutar(idSwitch, "comandar", () -> restClient.post()
                .uri("/switches/{id}/comandos", idSwitch)
                .contentType(MediaType.APPLICATION_JSON)
                .body(comando)
                .retrieve()
                .body(RespuestaComando.class));
    }

    private <T> T ejecutar(String idSwitch, String operacion, Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            if (status == HttpStatus.NOT_FOUND.value()) {
                throw new ServicioExternoException(
                        "El switch '" + idSwitch + "' no existe en el simulador (404).", e);
            }
            if (status == HttpStatus.UNAUTHORIZED.value()) {
                throw new ServicioExternoException("El simulador rechazó la credencial al " + operacion
                        + " el switch (401). Verificá SIMULADOR_API_KEY.", e);
            }
            throw new ServicioExternoException("El simulador respondió " + status + " al " + operacion
                    + " el switch '" + idSwitch + "': " + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            throw new ServicioExternoException("No se pudo contactar al simulador para " + operacion
                    + " el switch '" + idSwitch + "'. ¿Está levantado y SIMULADOR_URL es correcta? ("
                    + e.getMessage() + ")", e);
        } catch (RestClientException e) {
            throw new ServicioExternoException("Error inesperado al " + operacion + " el switch '"
                    + idSwitch + "': " + e.getMessage(), e);
        }
    }
}

