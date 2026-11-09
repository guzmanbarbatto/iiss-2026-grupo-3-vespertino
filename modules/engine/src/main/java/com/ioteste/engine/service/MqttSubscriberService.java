package com.ioteste.engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ioteste.engine.domain.ControladorRegistrado.BrokerMqtt;
import com.ioteste.engine.domain.Habitacion;
import com.ioteste.engine.domain.InventarioDTO;
import com.ioteste.engine.domain.InventarioReemplazado;
import com.ioteste.engine.domain.MensajeTermostato;
import com.ioteste.engine.repository.HistoricoRepository;
import com.ioteste.engine.repository.SitioRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Recibe las temperaturas de los termostatos por MQTT (§2) y las guarda en el histórico.
 *
 * <p>Se suscribe exactamente a los {@code topicTermostato} que declara el inventario: el estándar dice que
 * "el topic declarado es el que vale", así que no se asume ningún formato. La suscripción se ajusta en
 * cada conexión y en cada {@code PUT /sitio}.
 *
 * <p>El broker es el que anuncia el simulador al registrarnos (§6). {@code MQTT_BROKER_URL} lo reemplaza
 * cuando el nombre anunciado no es alcanzable desde la red Docker de este proyecto.
 */
@Service
public class MqttSubscriberService {

    private static final Logger LOG = LoggerFactory.getLogger(MqttSubscriberService.class);

    private static final long REINTENTO_MS = 5_000;
    private static final int QOS = 1;

    /**
     * Rango plausible (°C) de la temperatura de una habitación. Afuera no hay una lectura sino un sensor en falla:
     * −40 °C y 85 °C son los valores típicos de error de los sensores de temperatura.
     */
    static final double TEMPERATURA_MINIMA_C = -10.0;
    static final double TEMPERATURA_MAXIMA_C = 50.0;

    private final HistoricoRepository historico;
    private final SitioRepository sitioRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String brokerConfigurado;
    private final AtomicBoolean conexionIniciada = new AtomicBoolean(false);
    /** Topics cuyo termostato está mandando mensajes fuera del contrato (para avisar una sola vez). */
    private final FallasActivas fueraDeContrato = new FallasActivas();

    // Estado de la suscripción: se accede solo desde métodos synchronized
    private IMqttAsyncClient conectado;
    private Set<String> declarados = Set.of();
    private final Set<String> suscriptos = new LinkedHashSet<>();

    public MqttSubscriberService(
            HistoricoRepository historico,
            SitioRepository sitioRepository,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${mqtt.broker.url}") String brokerConfigurado) {
        this.historico = historico;
        this.sitioRepository = sitioRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.brokerConfigurado = brokerConfigurado;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alIniciar() {
        if (brokerConfigurado.isBlank()) {
            LOG.info("MQTT_BROKER_URL vacía: se usará el broker que anuncie el simulador al registrarnos");
        } else {
            conectarEnSegundoPlano(brokerConfigurado);
        }
    }

    @EventListener
    public void alAnunciarseBroker(BrokerMqtt broker) {
        if (brokerConfigurado.isBlank()) {
            conectarEnSegundoPlano(broker.url());
        }
    }

    /** Un inventario nuevo puede traer topics nuevos y dejar otros sin uso. */
    @EventListener
    public synchronized void inventarioReemplazado(InventarioReemplazado evento) {
        declarados = topicsDe(Optional.of(evento.nuevo()));
        sincronizarSuscripciones();
    }

    private void conectarEnSegundoPlano(String url) {
        if (!conexionIniciada.compareAndSet(false, true)) {
            return;
        }
        Thread hilo = new Thread(() -> conectar(url), "mqtt-conexion");
        hilo.setDaemon(true);
        hilo.start();
    }

    /**
     * Reintenta hasta lograr la primera conexión. Las caídas posteriores las resuelve la reconexión
     * automática de Paho, y {@link Suscripcion#connectComplete} vuelve a suscribir cada vez.
     */
    private void conectar(String url) {
        boolean avisado = false;
        while (!Thread.currentThread().isInterrupted()) {
            MqttAsyncClient cliente = null;
            try {
                cliente = new MqttAsyncClient(url, MqttAsyncClient.generateClientId(), new MemoryPersistence());
                cliente.setCallback(new Suscripcion(cliente));
                cliente.connect(opciones()).waitForCompletion();
                return;
            } catch (IllegalArgumentException e) {
                LOG.error("URL de broker MQTT inválida '{}': {}", url, e.getMessage());
                return;
            } catch (MqttException e) {
                cerrar(cliente);
                if (!avisado) {
                    LOG.warn("Broker MQTT {} no disponible ({}). Se reintenta cada {} s",
                            url, e.getMessage(), REINTENTO_MS / 1000);
                    avisado = true;
                } else {
                    LOG.debug("Broker MQTT {} sigue sin responder: {}", url, e.getMessage());
                }
                try {
                    Thread.sleep(REINTENTO_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static MqttConnectOptions opciones() {
        MqttConnectOptions opciones = new MqttConnectOptions();
        opciones.setAutomaticReconnect(true);
        opciones.setCleanSession(true);
        opciones.setConnectionTimeout(10);
        return opciones;
    }

    private static void cerrar(MqttAsyncClient cliente) {
        if (cliente != null) {
            try {
                cliente.close();
            } catch (MqttException e) {
                LOG.debug("No se pudo cerrar el cliente MQTT: {}", e.getMessage());
            }
        }
    }

    /**
     * Con {@code cleanSession=true} el broker olvida las suscripciones al cortarse la conexión, y la
     * reconexión automática de Paho no las restaura: en cada conexión se vuelve a suscribir a todo.
     */
    synchronized void alConectarse(IMqttAsyncClient cliente, boolean reconexion, String broker) {
        conectado = cliente;
        suscriptos.clear();
        LOG.info("{} al broker MQTT {}", reconexion ? "Reconectado" : "Conectado", broker);
        try {
            declarados = topicsDe(sitioRepository.obtenerInventario());
        } catch (RuntimeException e) {
            LOG.warn("No se pudo leer el inventario para suscribirse ({}); se usan los últimos topics conocidos",
                    e.getMessage());
        }
        if (declarados.isEmpty()) {
            LOG.info("Todavía no hay un sitio cargado: la suscripción se hará al recibir el PUT /sitio");
        }
        sincronizarSuscripciones();
    }

    /** Lleva las suscripciones del broker a los topics declarados: agrega los que faltan y quita los que sobran. */
    private void sincronizarSuscripciones() {
        if (conectado == null || !conectado.isConnected()) {
            return; // se hará al conectarse
        }
        Set<String> sobran = new LinkedHashSet<>(suscriptos);
        sobran.removeAll(declarados);
        Set<String> faltan = new LinkedHashSet<>(declarados);
        faltan.removeAll(suscriptos);
        try {
            if (!sobran.isEmpty()) {
                conectado.unsubscribe(sobran.toArray(String[]::new), null, avisarSiFalla("dejar de escuchar"));
                suscriptos.removeAll(sobran);
                LOG.info("MQTT: deja de escuchar {} (ya no están en el inventario)", termostatos(sobran));
            }
            if (!faltan.isEmpty()) {
                int[] qos = new int[faltan.size()];
                Arrays.fill(qos, QOS);
                conectado.subscribe(faltan.toArray(String[]::new), qos, null, avisarSiFalla("suscribirse a"));
                suscriptos.addAll(faltan);
                LOG.info("MQTT: suscripto a {}", termostatos(faltan));
            }
        } catch (MqttException e) {
            LOG.error("No se pudo actualizar la suscripción MQTT: {}", e.getMessage());
        }
    }

    private static IMqttActionListener avisarSiFalla(String operacion) {
        return new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken token) {
                // Nada que avisar
            }

            @Override
            public void onFailure(IMqttToken token, Throwable causa) {
                LOG.error("El broker rechazó {} {}: {}", operacion,
                        Arrays.toString(token.getTopics()), causa.getMessage());
            }
        };
    }

    private static Set<String> topicsDe(Optional<InventarioDTO> inventario) {
        return inventario.<Set<String>>map(i -> i.habitaciones().stream()
                        .map(Habitacion::topicTermostato)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .orElse(Set.of());
    }

    /**
     * Guarda la lectura de un mensaje. Nunca lanza: un mensaje inválido no debe cortar la suscripción.
     *
     * <p>Un mensaje fuera del contrato (§2) no es una lectura: se descarta, y si el termostato no vuelve a mandar
     * una lectura válida, el fail-safe apaga su habitación como si estuviera callado. Se avisa una vez cuando un
     * termostato empieza a mandar mal y otra cuando se recupera, no con cada mensaje.
     */
    void procesar(String topic, byte[] payload) {
        MensajeTermostato mensaje;
        try {
            mensaje = interpretar(payload);
        } catch (FueraDeContrato e) {
            if (fueraDeContrato.empezo(topic)) {
                LOG.warn("El termostato {} manda mensajes fuera del contrato ({}): se descartan y, si no vuelve a "
                        + "mandar lecturas válidas, su habitación se apaga por seguridad", termostato(topic),
                        e.getMessage());
            } else {
                LOG.debug("Mensaje de {} fuera del contrato: {}", topic, e.getMessage());
            }
            return;
        }
        if (fueraDeContrato.termino(topic)) {
            LOG.info("El termostato {} vuelve a mandar lecturas válidas", termostato(topic));
        }
        try {
            Instant recibidaEn = clock.instant();
            Instant medidaEn = mensaje.ts() != null && mensaje.ts() > 0
                    ? Instant.ofEpochMilli(Math.round(mensaje.ts() * 1000))
                    : recibidaEn;
            if (historico.registrar(topic, medidaEn, recibidaEn, mensaje.tC()) > 0) {
                LOG.info("Temperatura {}: {} °C", termostato(topic), mensaje.tC());
            } else {
                LOG.debug("El topic {} no corresponde a ninguna habitación", topic);
            }
        } catch (RuntimeException e) {
            LOG.error("No se pudo registrar la lectura de {}: {}", topic, e.getMessage());
        }
    }

    /**
     * El mensaje del §2: un objeto JSON con {@code tC} numérica y plausible. {@code ts} (epoch en segundos) puede
     * faltar. Se exige que {@code tC} sea un número JSON: Jackson, por defecto, convertiría {@code "20.0"} sin avisar.
     */
    private MensajeTermostato interpretar(byte[] payload) throws FueraDeContrato {
        JsonNode json;
        try {
            json = objectMapper.readTree(payload);
        } catch (IOException e) {
            throw new FueraDeContrato("no es JSON");
        }
        if (json == null || !json.isObject()) {
            throw new FueraDeContrato("no es un objeto JSON");
        }
        JsonNode tC = json.get("tC");
        if (tC == null || tC.isNull()) {
            throw new FueraDeContrato("sin tC");
        }
        if (!tC.isNumber()) {
            throw new FueraDeContrato("tC no es un número: " + tC);
        }
        double temperatura = tC.asDouble();
        if (temperatura < TEMPERATURA_MINIMA_C || temperatura > TEMPERATURA_MAXIMA_C) {
            throw new FueraDeContrato("tC fuera de rango: " + tC + " °C");
        }
        JsonNode ts = json.get("ts");
        return new MensajeTermostato(temperatura, ts != null && ts.isNumber() ? ts.asDouble() : null);
    }

    /** Por qué un mensaje no es una lectura válida. */
    private static final class FueraDeContrato extends Exception {
        FueraDeContrato(String motivo) {
            super(motivo, null, false, false);
        }
    }

    /**
     * Nombre corto para el log: el id del termostato si el topic sigue la convención
     * ({@code ht-room1/status/temperature:0} → {@code ht-room1}); si no, el topic completo.
     */
    static String termostato(String topic) {
        int convencion = topic.indexOf("/status/");
        return convencion > 0 ? topic.substring(0, convencion) : topic;
    }

    private static String termostatos(Collection<String> topics) {
        return topics.stream().map(MqttSubscriberService::termostato).collect(Collectors.joining(", "));
    }

    /** Callback de un cliente MQTT. */
    final class Suscripcion implements MqttCallbackExtended {

        private final IMqttAsyncClient cliente;

        Suscripcion(IMqttAsyncClient cliente) {
            this.cliente = cliente;
        }

        @Override
        public void connectComplete(boolean reconexion, String broker) {
            alConectarse(cliente, reconexion, broker);
        }

        @Override
        public void connectionLost(Throwable causa) {
            LOG.warn("Se perdió la conexión con el broker MQTT ({}). Reconectando...", causa.getMessage());
        }

        @Override
        public void messageArrived(String topic, MqttMessage mensaje) {
            procesar(topic, mensaje.getPayload());
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // Solo se suscribe: nunca publica
        }
    }
}
