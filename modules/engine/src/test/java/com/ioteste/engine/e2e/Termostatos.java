package com.ioteste.engine.e2e;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Los termostatos del sitio: cada {@link #PERIODO} publican la temperatura de su habitación con el payload del
 * estándar (§2), como el simulador de la cátedra. El test decide qué mide cada uno o lo hace callar. Si el
 * broker se cae, se reconectan solos y siguen publicando.
 */
final class Termostatos implements AutoCloseable {

    static final Duration PERIODO = Duration.ofMillis(500);

    private final MqttClient cliente;
    private final ScheduledExecutorService hilo = Executors.newSingleThreadScheduledExecutor();
    /** Temperatura que publica cada topic. Un topic ausente es un termostato callado. */
    private final Map<String, Double> temperaturas = new ConcurrentHashMap<>();
    /** Mensaje tal cual que publica un termostato fuera del contrato, en lugar de su temperatura. */
    private final Map<String, String> crudos = new ConcurrentHashMap<>();

    Termostatos(String brokerUrl) throws MqttException {
        cliente = new MqttClient(brokerUrl, "termostatos-e2e", new MemoryPersistence());
        cliente.setTimeToWait(2000);
        MqttConnectOptions opciones = new MqttConnectOptions();
        opciones.setAutomaticReconnect(true);
        opciones.setCleanSession(true);
        cliente.connect(opciones);
        hilo.scheduleAtFixedRate(this::publicar, 0, PERIODO.toMillis(), TimeUnit.MILLISECONDS);
    }

    void medir(String topic, double temperaturaC) {
        crudos.remove(topic);
        temperaturas.put(topic, temperaturaC);
    }

    /** El termostato publica {@code payload} tal cual (por ejemplo, fuera del contrato) hasta que vuelva a medir. */
    void publicarTalCual(String topic, String payload) {
        temperaturas.remove(topic);
        crudos.put(topic, payload);
    }

    void callar(String topic) {
        temperaturas.remove(topic);
        crudos.remove(topic);
    }

    private void publicar() {
        temperaturas.forEach((topic, temperaturaC) -> enviar(topic, String.format(Locale.ROOT,
                "{\"id\":0,\"tC\":%.1f,\"tF\":%.1f,\"ts\":%.3f}",
                temperaturaC, temperaturaC * 9 / 5 + 32, System.currentTimeMillis() / 1000.0)));
        crudos.forEach(this::enviar);
    }

    private void enviar(String topic, String payload) {
        try {
            cliente.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 0, false);
        } catch (MqttException | RuntimeException e) {
            // Broker caído o reconectando: se vuelve a publicar en el próximo período
        }
    }

    @Override
    public void close() throws MqttException {
        hilo.shutdownNow();
        cliente.disconnectForcibly(0, 1000);
        cliente.close();
    }
}
