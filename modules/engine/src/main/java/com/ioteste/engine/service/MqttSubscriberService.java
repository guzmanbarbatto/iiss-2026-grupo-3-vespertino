package com.ioteste.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ioteste.engine.domain.ControladorRegistrado.BrokerMqtt;
import com.ioteste.engine.domain.MensajeTermostato;
import com.ioteste.engine.repository.HistoricoRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
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
 * <p>El broker es el que anuncia el simulador al registrarnos (§6). {@code MQTT_BROKER_URL} lo reemplaza
 * cuando el nombre anunciado no es alcanzable desde la red Docker de este proyecto.
 */
@Service
public class MqttSubscriberService {

    private static final Logger LOG = LoggerFactory.getLogger(MqttSubscriberService.class);

    static final String TOPICO_TEMPERATURAS = "+/status/temperature:0";
    private static final long REINTENTO_MS = 5_000;

    private final HistoricoRepository historico;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String brokerConfigurado;
    private final AtomicBoolean conexionIniciada = new AtomicBoolean(false);

    public MqttSubscriberService(
            HistoricoRepository historico,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${mqtt.broker.url}") String brokerConfigurado) {
        this.historico = historico;
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
                LOG.warn("Broker MQTT {} no disponible ({}). Reintentando en {} s...",
                        url, e.getMessage(), REINTENTO_MS / 1000);
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

    /** Guarda la lectura de un mensaje. Nunca lanza: un mensaje inválido no debe cortar la suscripción. */
    void procesar(String topic, byte[] payload) {
        try {
            MensajeTermostato mensaje = objectMapper.readValue(payload, MensajeTermostato.class);
            if (mensaje.tC() == null) {
                LOG.warn("Mensaje de {} sin tC: se descarta", topic);
                return;
            }
            Instant fechaHora = mensaje.ts() != null && mensaje.ts() > 0
                    ? Instant.ofEpochMilli(Math.round(mensaje.ts() * 1000))
                    : clock.instant();
            if (historico.registrar(topic, fechaHora, mensaje.tC()) > 0) {
                LOG.info("Temperatura {}: {} °C", termostato(topic), mensaje.tC());
            } else {
                LOG.debug("El topic {} no corresponde a ninguna habitación", topic);
            }
        } catch (IOException e) {
            LOG.warn("Payload inválido en {}: {}", topic, e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("No se pudo registrar la lectura de {}: {}", topic, e.getMessage());
        }
    }

    /** Id del termostato a partir de su topic ({@code ht-room1/status/temperature:0} → {@code ht-room1}). */
    static String termostato(String topic) {
        int barra = topic.indexOf('/');
        return barra > 0 ? topic.substring(0, barra) : topic;
    }

    /**
     * Callback de un cliente. Con {@code cleanSession=true} el broker olvida las suscripciones al cortarse
     * la conexión, y la reconexión automática de Paho no las restaura: hay que suscribir en cada conexión.
     */
    final class Suscripcion implements MqttCallbackExtended {

        private final IMqttAsyncClient cliente;

        Suscripcion(IMqttAsyncClient cliente) {
            this.cliente = cliente;
        }

        @Override
        public void connectComplete(boolean reconexion, String broker) {
            try {
                cliente.subscribe(TOPICO_TEMPERATURAS, 1);
                LOG.info("{} al broker MQTT {} y suscripto a {}",
                        reconexion ? "Reconectado" : "Conectado", broker, TOPICO_TEMPERATURAS);
            } catch (MqttException e) {
                LOG.error("No se pudo suscribir a {} en {}: {}", TOPICO_TEMPERATURAS, broker, e.getMessage());
            }
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
