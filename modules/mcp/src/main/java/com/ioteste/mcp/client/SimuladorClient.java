package com.ioteste.mcp.client;

import com.ioteste.mcp.client.dto.SimuladorDtos.ComandoSwitch;
import com.ioteste.mcp.client.dto.SimuladorDtos.EstadoSwitch;
import com.ioteste.mcp.client.dto.SimuladorDtos.RespuestaComando;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente de los switches ({@code simulador.openapi-v2.yaml}).
 *
 * <p>Igual que el engine, cada switch se usa en el {@code urlSwitch} que declara su habitación en el inventario
 * (§3 y §4 de {@code interop-v2}). {@code SWITCHES_URL} los reemplaza a todos cuando ese nombre no es alcanzable
 * desde la red del MCP. Una habitación sin {@code urlSwitch} usa {@code SIMULADOR_URL}.
 */
@Component
public class SimuladorClient {

    private final RestClient restClient;
    private final String simuladorUrl;
    private final String switchesUrl;

    public SimuladorClient(
            @Qualifier("simuladorRestClient") RestClient restClient,
            @Value("${simulador.url}") String simuladorUrl,
            @Value("${switches.url:}") String switchesUrl) {
        this.restClient = restClient;
        this.simuladorUrl = simuladorUrl;
        this.switchesUrl = switchesUrl == null ? "" : switchesUrl.trim();
    }

    /** {@code GET {urlSwitch}/switches/{idSwitch}}. */
    public EstadoSwitch consultarSwitch(String urlSwitch, String idSwitch) {
        String base = base(urlSwitch);
        return ejecutar(base, idSwitch, "consultar", () -> restClient.get()
                .uri(base + "/switches/{id}", idSwitch)
                .retrieve()
                .body(EstadoSwitch.class));
    }

    /** {@code POST {urlSwitch}/switches/{idSwitch}/comandos} con {@code accion} ON u OFF. */
    public RespuestaComando comandarSwitch(String urlSwitch, String idSwitch, boolean encender) {
        String base = base(urlSwitch);
        ComandoSwitch comando = new ComandoSwitch(encender ? "ON" : "OFF");
        return ejecutar(base, idSwitch, "comandar", () -> restClient.post()
                .uri(base + "/switches/{id}/comandos", idSwitch)
                .contentType(MediaType.APPLICATION_JSON)
                .body(comando)
                .retrieve()
                .body(RespuestaComando.class));
    }

    /** Dónde está el switch: {@code SWITCHES_URL} si se definió; si no, su {@code urlSwitch}. */
    private String base(String urlSwitch) {
        String base = !switchesUrl.isEmpty() ? switchesUrl
                : urlSwitch != null && !urlSwitch.isBlank() ? urlSwitch.trim()
                : simuladorUrl;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private <T> T ejecutar(String base, String idSwitch, String operacion, Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            if (status == HttpStatus.NOT_FOUND.value()) {
                throw new ServicioExternoException(
                        "El switch '" + idSwitch + "' no existe en " + base + " (404).", e);
            }
            if (status == HttpStatus.UNAUTHORIZED.value()) {
                throw new ServicioExternoException("El simulador rechazó la credencial al " + operacion
                        + " el switch (401). Verificá SIMULADOR_API_KEY.", e);
            }
            throw new ServicioExternoException("El simulador respondió " + status + " al " + operacion
                    + " el switch '" + idSwitch + "': " + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            throw new ServicioExternoException("No se pudo contactar al switch '" + idSwitch + "' en " + base
                    + " para " + operacion + "lo. ¿Está levantado el simulador? Si esa dirección no es alcanzable "
                    + "desde el MCP, definí SWITCHES_URL. (" + e.getMessage() + ")", e);
        } catch (RestClientException e) {
            throw new ServicioExternoException("Error inesperado al " + operacion + " el switch '"
                    + idSwitch + "': " + e.getMessage(), e);
        }
    }
}
