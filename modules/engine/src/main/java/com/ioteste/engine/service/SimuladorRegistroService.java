package com.ioteste.engine.service;

import com.ioteste.engine.domain.AltaControlador;
import com.ioteste.engine.domain.ControladorRegistrado;
import java.net.ConnectException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Registro del controlador ante el simulador (§6).
 *
 * <p>Reintenta hasta registrarse. Después verifica cada minuto que el simulador lo siga conociendo: un
 * simulador reiniciado olvida los registros, y sin registro no puede llamar a este controlador.
 *
 * <p>Esa verificación usa el listado de controladores, que no está en el estándar: un simulador que no lo ofrece
 * (como {@code ecowarm-check}) no puede decir si se reinició. Por eso, si el simulador rechazó la conexión (no estaba
 * corriendo), cuando vuelve a responder el controlador se registra de nuevo sin preguntar: el alta es idempotente por
 * etiqueta (§6). Antes daba el registro por vigente y quedaba sin registrar ante un simulador nuevo.
 */
@Service
public class SimuladorRegistroService {

    private static final Logger LOG = LoggerFactory.getLogger(SimuladorRegistroService.class);

    static final String ETIQUETA = "control-grupo-3";
    static final Duration REINTENTO = Duration.ofSeconds(5);
    static final Duration VERIFICACION = Duration.ofMinutes(1);
    private static final int TIMEOUT_MS = 2000;

    private final RestTemplate restTemplate;
    private final ApplicationEventPublisher eventos;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final String endpoint;
    private final HttpHeaders cabeceras;
    private final AltaControlador alta;

    private static final String SIMULADOR = "simulador";
    private final FallasActivas fallas = new FallasActivas();

    /** {@code null} mientras no se logró el registro. */
    private volatile Instant proximaVerificacion;

    @Autowired
    public SimuladorRegistroService(
            ApplicationEventPublisher eventos,
            TaskScheduler scheduler,
            Clock clock,
            @Value("${simulador.url}") String simuladorUrl,
            @Value("${simulador.api.key}") String simuladorApiKey,
            @Value("${app.api.key}") String miApiKey,
            @Value("${controlador.base.url}") String miBaseUrl) {
        this(conTimeouts(), eventos, scheduler, clock, simuladorUrl, simuladorApiKey, miApiKey, miBaseUrl);
    }

    SimuladorRegistroService(RestTemplate restTemplate, ApplicationEventPublisher eventos, TaskScheduler scheduler,
            Clock clock, String simuladorUrl, String simuladorApiKey, String miApiKey, String miBaseUrl) {
        this.restTemplate = restTemplate;
        this.eventos = eventos;
        this.scheduler = scheduler;
        this.clock = clock;
        this.endpoint = simuladorUrl + "/controladores";
        this.cabeceras = new HttpHeaders();
        this.cabeceras.set("X-API-Key", simuladorApiKey);
        this.alta = new AltaControlador(ETIQUETA, miBaseUrl, miApiKey);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciar() {
        scheduler.scheduleWithFixedDelay(this::mantenerRegistro, REINTENTO);
    }

    void mantenerRegistro() {
        Instant ahora = clock.instant();
        Instant verificar = proximaVerificacion;
        if (verificar != null && ahora.isBefore(verificar)) {
            return;
        }
        try {
            if (verificar != null && sigueRegistrado()) {
                respondio();
                proximaVerificacion = ahora.plus(VERIFICACION);
                return;
            }
            if (verificar != null) {
                LOG.warn("El simulador ya no tiene registrado a '{}' (¿se reinició?): registrando de nuevo", ETIQUETA);
            }
            registrar();
            respondio();
            proximaVerificacion = ahora.plus(VERIFICACION);
        } catch (RestClientException e) {
            if (e.getMostSpecificCause() instanceof ConnectException) {
                // No estaba corriendo: el que vuelva puede ser un simulador nuevo, sin este registro. Un timeout o
                // un error HTTP no cuentan: ese simulador sigue corriendo, y registrarse de nuevo en medio de una
                // corrida podría reiniciarla
                proximaVerificacion = null;
            }
            if (fallas.empezo(SIMULADOR)) {
                LOG.warn("El simulador no responde en {} ({}). Se reintenta cada {} s; se avisará cuando responda",
                        endpoint, e.getMessage(), REINTENTO.toSeconds());
            } else {
                LOG.debug("El simulador sigue sin responder: {}", e.getMessage());
            }
        }
    }

    private void respondio() {
        if (fallas.termino(SIMULADOR)) {
            LOG.info("El simulador vuelve a responder en {}", endpoint);
        }
    }

    private void registrar() {
        ControladorRegistrado respuesta = restTemplate.postForObject(
                endpoint, new HttpEntity<>(alta, cabeceras), ControladorRegistrado.class);
        LOG.info("Registrado en el simulador como '{}' ({})", ETIQUETA, alta.baseUrl());
        if (respuesta != null && respuesta.mqtt() != null) {
            LOG.info("El simulador anuncia el broker MQTT {}", respuesta.mqtt().url());
            eventos.publishEvent(respuesta.mqtt());
        }
    }

    /** Si el simulador no ofrece el listado (fuera del estándar), se asume que el registro sigue vigente. */
    private boolean sigueRegistrado() {
        try {
            ControladorRegistrado[] registrados = restTemplate.exchange(
                    endpoint, HttpMethod.GET, new HttpEntity<>(cabeceras), ControladorRegistrado[].class).getBody();
            return registrados != null && Arrays.stream(registrados).anyMatch(c -> ETIQUETA.equals(c.etiqueta()));
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.MethodNotAllowed e) {
            return true;
        }
    }

    private static RestTemplate conTimeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
