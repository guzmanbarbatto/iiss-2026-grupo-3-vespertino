package com.ioteste.engine.service;

import com.ioteste.core.domain.EstadoSwitch;
import com.ioteste.engine.domain.ComandoSwitch;
import com.ioteste.engine.domain.RespuestaEstadoSwitch;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 * Cliente REST de los switches (§3 y §4).
 *
 * <p>Cada switch se consulta y se comanda en el {@code urlSwitch} que declara su habitación, como dice el
 * estándar: los switches de un sitio pueden estar en servidores distintos. {@code SWITCHES_URL} los reemplaza
 * a todos cuando el nombre anunciado no es alcanzable desde esta red (el mismo caso que {@code MQTT_BROKER_URL}
 * para el broker).
 *
 * <p>El estado real se relee como mucho cada {@link #ESTADO_VIGENTE}, no en cada ciclo: con aceleración alta
 * el ciclo corre cada 0,5 s, y consultar todos los switches en cada uno multiplicaba las llamadas al simulador.
 * Un comando aplicado deja el estado conocido sin consultarlo; una falla o un estado desconocido obligan a
 * releerlo en el ciclo siguiente. Un cambio hecho desde afuera (MCP, consola del simulador) se detecta en
 * {@link #ESTADO_VIGENTE} como máximo.
 */
@Service
public class SwitchClientService {

    private static final Logger LOG = LoggerFactory.getLogger(SwitchClientService.class);
    /**
     * Para recibir la respuesta. Un switch de la red local contesta en milisegundos (el del simulador, en menos de
     * uno); uno que no contestó en 1 s se da por fallado y se reintenta en el ciclo siguiente. Cada segundo de
     * espera es un minuto simulado con ×60 en el que el control no evalúa.
     */
    static final int READ_TIMEOUT_MS = 1000;
    /**
     * Para abrir la conexión, más que para la respuesta: si se pierde el primer paquete de conexión (pasa en Wi-Fi),
     * el reintento sale recién al segundo, y con 1 s se cortaba justo antes. Un switch que no contesta (la falla F05
     * de {@code ecowarm-run}) acepta la conexión: lo corta el timeout de respuesta.
     */
    static final int CONNECT_TIMEOUT_MS = 3000;

    /** Cuánto tiempo real se confía en el último estado conocido de un switch antes de volver a consultarlo. */
    static final Duration ESTADO_VIGENTE = Duration.ofSeconds(5);

    private final RestTemplate restTemplate;
    private final Clock clock;
    private final String simuladorUrl;
    private final String switchesUrl;
    private final String simuladorApiKey;
    private final FallasActivas fallas = new FallasActivas();
    /** Último estado conocido de cada switch, por su URL. */
    private final Map<String, EstadoConocido> conocidos = new ConcurrentHashMap<>();

    private record EstadoConocido(EstadoSwitch estado, Instant vigenteHasta) { }

    @Autowired
    public SwitchClientService(
            Clock clock,
            @Value("${simulador.url}") String simuladorUrl,
            @Value("${switches.url:}") String switchesUrl,
            @Value("${simulador.api.key}") String simuladorApiKey) {
        this(conTimeouts(), clock, simuladorUrl, switchesUrl, simuladorApiKey);
    }

    SwitchClientService(RestTemplate restTemplate, Clock clock, String simuladorUrl, String switchesUrl,
            String simuladorApiKey) {
        this.restTemplate = restTemplate;
        this.clock = clock;
        this.simuladorUrl = simuladorUrl;
        this.switchesUrl = switchesUrl == null ? "" : switchesUrl.trim();
        this.simuladorApiKey = simuladorApiKey;
    }

    /**
     * Estado del switch: el último conocido mientras siga vigente; si no, se consulta. Si la consulta falla (o la
     * respuesta no se entiende), el estado es {@link EstadoSwitch#DESCONOCIDO} y el controlador lo comandará
     * explícitamente.
     *
     * <p>Salvo que el último estado conocido fuera {@code ON}: una consulta que falla no cambia el switch, así que se
     * lo sigue contando encendido (su potencia sigue ocupada) y se vuelve a consultar en el ciclo siguiente. Si no,
     * con 50 switches por Wi-Fi, un {@code Connect timed out} ocasional hacía apagar una habitación que estaba
     * calentando para darle su lugar a otra.
     */
    public EstadoSwitch consultarEstado(String urlSwitch, String idSwitch) {
        String base = base(urlSwitch);
        EstadoConocido conocido = conocidos.get(clave(base, idSwitch));
        if (conocido != null && clock.instant().isBefore(conocido.vigenteHasta())) {
            return conocido.estado();
        }
        EstadoSwitch estado = leerEstado(base, idSwitch);
        if (estado == EstadoSwitch.DESCONOCIDO && conocido != null && conocido.estado() == EstadoSwitch.ON) {
            return EstadoSwitch.ON; // sigue vencido: se vuelve a consultar en el ciclo siguiente
        }
        recordar(base, idSwitch, estado);
        return estado;
    }

    private EstadoSwitch leerEstado(String base, String idSwitch) {
        try {
            RespuestaEstadoSwitch respuesta = restTemplate.exchange(base + "/switches/{id}", HttpMethod.GET,
                    new HttpEntity<>(cabeceras()), RespuestaEstadoSwitch.class, idSwitch).getBody();
            respondio(idSwitch);
            return estadoDe(respuesta == null ? null : respuesta.estado());
        } catch (RestClientException e) {
            noRespondio(idSwitch, "consultar el", e);
            return EstadoSwitch.DESCONOCIDO;
        }
    }

    /** Resultado de un comando: el simulador lo aplicó, el switch no existe (404) o no se pudo enviar. */
    public enum Resultado { APLICADO, INEXISTENTE, FALLIDO }

    /** Envía ON u OFF al switch. Nunca lanza: una falla en un switch no debe frenar el ciclo de control. */
    public Resultado comandarSwitch(String urlSwitch, String idSwitch, String accion) {
        String base = base(urlSwitch);
        try {
            restTemplate.exchange(base + "/switches/{id}/comandos", HttpMethod.POST,
                    new HttpEntity<>(new ComandoSwitch(accion), cabeceras()), String.class, idSwitch);
            respondio(idSwitch);
            recordar(base, idSwitch, estadoDe(accion));
            return Resultado.APLICADO;
        } catch (HttpClientErrorException.NotFound e) {
            recordar(base, idSwitch, EstadoSwitch.DESCONOCIDO);
            noRespondio(idSwitch, "enviar " + accion + " al", e);
            return Resultado.INEXISTENTE;
        } catch (RestClientException e) {
            recordar(base, idSwitch, EstadoSwitch.DESCONOCIDO);
            noRespondio(idSwitch, "enviar " + accion + " al", e);
            return Resultado.FALLIDO;
        }
    }

    /** Olvida todos los estados conocidos: el próximo pedido de cada uno se consulta. Se usa al iniciar el control. */
    public void olvidarEstados() {
        conocidos.clear();
    }

    /** Un estado desconocido no se recuerda: se vuelve a consultar en el ciclo siguiente. */
    private void recordar(String base, String idSwitch, EstadoSwitch estado) {
        if (estado == EstadoSwitch.DESCONOCIDO) {
            conocidos.remove(clave(base, idSwitch));
        } else {
            conocidos.put(clave(base, idSwitch), new EstadoConocido(estado, clock.instant().plus(ESTADO_VIGENTE)));
        }
    }

    /**
     * Dónde está el switch: {@code SWITCHES_URL} si se definió; si no, su {@code urlSwitch}. Si falta (no
     * debería: el inventario lo exige), {@code SIMULADOR_URL}.
     */
    private String base(String urlSwitch) {
        String base = !switchesUrl.isEmpty() ? switchesUrl
                : urlSwitch != null && !urlSwitch.isBlank() ? urlSwitch.trim()
                : simuladorUrl;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static String clave(String base, String idSwitch) {
        return base + "/switches/" + idSwitch;
    }

    private static EstadoSwitch estadoDe(String estado) {
        if ("ON".equals(estado)) {
            return EstadoSwitch.ON;
        }
        if ("OFF".equals(estado)) {
            return EstadoSwitch.OFF;
        }
        return EstadoSwitch.DESCONOCIDO;
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
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
