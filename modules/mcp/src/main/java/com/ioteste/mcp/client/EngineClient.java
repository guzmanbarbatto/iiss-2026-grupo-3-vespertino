package com.ioteste.mcp.client;

import com.ioteste.mcp.client.dto.EngineDtos.EstadoControl;
import com.ioteste.mcp.client.dto.EngineDtos.Inventario;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Cliente de la API REST del engine EcoWarm ({@code controlador.openapi-v2.yaml}). */
@Component
public class EngineClient {

    private final RestClient restClient;

    public EngineClient(@Qualifier("engineRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** {@code GET /sitio}. Vacío si el engine todavía no tiene un sitio cargado (404). */
    public Optional<Inventario> obtenerSitio() {
        try {
            return Optional.ofNullable(
                    restClient.get().uri("/sitio").retrieve().body(Inventario.class));
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw traducir("consultar el sitio", e);
        } catch (RestClientException e) {
            throw traducir("consultar el sitio", e);
        }
    }

    /** {@code GET /control}. */
    public EstadoControl obtenerControl() {
        return ejecutar("consultar el estado del control",
                () -> restClient.get().uri("/control").retrieve().body(EstadoControl.class));
    }

    /** {@code POST /control/comandos} con {@code accion = INICIAR}. */
    public EstadoControl iniciarControl(String fechaHora, Double factorTiempo) {
        return comandar(new ComandoControl("INICIAR", fechaHora, factorTiempo), "iniciar el control");
    }

    /** {@code POST /control/comandos} con {@code accion = DETENER}. */
    public EstadoControl detenerControl() {
        return comandar(new ComandoControl("DETENER", null, null), "detener el control");
    }

    private EstadoControl comandar(ComandoControl comando, String operacion) {
        return ejecutar(operacion, () -> restClient.post()
                .uri("/control/comandos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(comando)
                .retrieve()
                .body(EstadoControl.class));
    }

    private <T> T ejecutar(String operacion, Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (RestClientException e) {
            throw traducir(operacion, e);
        }
    }

    private ServicioExternoException traducir(String operacion, RestClientException e) {
        if (e instanceof HttpStatusCodeException http) {
            int status = http.getStatusCode().value();
            if (status == HttpStatus.UNAUTHORIZED.value()) {
                return new ServicioExternoException("El engine rechazó la credencial al " + operacion
                        + " (401). Verificá que APP_API_KEY del MCP coincida con la del engine.", e);
            }
            return new ServicioExternoException("El engine respondió " + status + " al " + operacion
                    + ": " + http.getResponseBodyAsString(), e);
        }
        if (e instanceof ResourceAccessException) {
            return new ServicioExternoException("No se pudo contactar al engine para " + operacion
                    + ". ¿Está levantado y ENGINE_URL es correcta? (" + e.getMessage() + ")", e);
        }
        return new ServicioExternoException("Error inesperado al " + operacion + ": " + e.getMessage(), e);
    }

    /** Cuerpo de {@code POST /control/comandos}; los campos nulos no se envían. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ComandoControl(String accion, String fechaHora, Double factorTiempo) {
    }
}

