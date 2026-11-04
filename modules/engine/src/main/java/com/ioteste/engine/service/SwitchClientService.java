package com.ioteste.engine.service;

import com.ioteste.core.domain.EstadoSwitch;
import com.ioteste.engine.domain.ComandoSwitch;
import com.ioteste.engine.domain.RespuestaEstadoSwitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Cliente REST de los switches del simulador (§3 y §4).
 *
 * <p>Usa {@code SIMULADOR_URL} como base para todos los switches en lugar del {@code urlSwitch} del
 * inventario: el simulador anuncia su nombre dentro de la red del laboratorio, que no es alcanzable desde
 * la red Docker de este proyecto.
 */
@Service
public class SwitchClientService {

    private static final Logger LOG = LoggerFactory.getLogger(SwitchClientService.class);
    private static final int TIMEOUT_MS = 2000;

    private final RestTemplate restTemplate;
    private final String simuladorUrl;
    private final String simuladorApiKey;
    private final FallasActivas fallas = new FallasActivas();

    @Autowired
    public SwitchClientService(
            @Value("${simulador.url}") String simuladorUrl,
            @Value("${simulador.api.key}") String simuladorApiKey) {
        this(conTimeouts(), simuladorUrl, simuladorApiKey);
    }

    SwitchClientService(RestTemplate restTemplate, String simuladorUrl, String simuladorApiKey) {
        this.restTemplate = restTemplate;
        this.simuladorUrl = simuladorUrl;
        this.simuladorApiKey = simuladorApiKey;
    }

    /**
     * Estado vigente del switch. Se consulta en cada ciclo: es la única fuente de verdad, porque el switch
     * también puede accionarse desde afuera (MCP, consola del simulador) o reiniciarse. Si la consulta falla
     * el estado es {@link EstadoSwitch#DESCONOCIDO} y el controlador lo comandará explícitamente.
     */
    public EstadoSwitch consultarEstado(String idSwitch) {
        try {
            RespuestaEstadoSwitch respuesta = restTemplate.exchange(
                    simuladorUrl + "/switches/{id}", HttpMethod.GET, new HttpEntity<>(cabeceras()),
                    RespuestaEstadoSwitch.class, idSwitch).getBody();
            respondio(idSwitch);
            String estado = respuesta == null ? null : respuesta.estado();
            if ("ON".equals(estado)) {
                return EstadoSwitch.ON;
            }
            if ("OFF".equals(estado)) {
                return EstadoSwitch.OFF;
            }
            return EstadoSwitch.DESCONOCIDO;
        } catch (RestClientException e) {
            noRespondio(idSwitch, "consultar el", e);
            return EstadoSwitch.DESCONOCIDO;
        }
    }

    /** Resultado de un comando: el simulador lo aplicó, el switch no existe (404) o no se pudo enviar. */
    public enum Resultado { APLICADO, INEXISTENTE, FALLIDO }

    /** Envía ON u OFF al switch. Nunca lanza: una falla en un switch no debe frenar el ciclo de control. */
    public Resultado comandarSwitch(String idSwitch, String accion) {
        try {
            restTemplate.exchange(simuladorUrl + "/switches/{id}/comandos", HttpMethod.POST,
                    new HttpEntity<>(new ComandoSwitch(accion), cabeceras()), String.class, idSwitch);
            respondio(idSwitch);
            return Resultado.APLICADO;
        } catch (HttpClientErrorException.NotFound e) {
            noRespondio(idSwitch, "enviar " + accion + " al", e);
            return Resultado.INEXISTENTE;
        } catch (RestClientException e) {
            noRespondio(idSwitch, "enviar " + accion + " al", e);
            return Resultado.FALLIDO;
        }
    }

    /** Avisa una vez cuando el switch deja de responder; mientras siga caído, el detalle queda en DEBUG. */
    private void noRespondio(String idSwitch, String operacion, RestClientException e) {
        if (fallas.empezo(idSwitch)) {
            LOG.warn("No se pudo {} switch {}: {} (se reintenta en cada ciclo; se avisará cuando responda)",
                    operacion, idSwitch, e.getMessage());
        } else {
            LOG.debug("El switch {} sigue sin responder: {}", idSwitch, e.getMessage());
        }
    }

    private void respondio(String idSwitch) {
        if (fallas.termino(idSwitch)) {
            LOG.info("El switch {} vuelve a responder", idSwitch);
        }
    }

    private HttpHeaders cabeceras() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", simuladorApiKey);
        return headers;
    }

    private static RestTemplate conTimeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
