
package com.ioteste.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ioteste.core.domain.AccionSwitch;
import com.ioteste.core.domain.EstadoSwitch;
import com.ioteste.core.domain.HabitacionDTO;
import com.ioteste.core.domain.Orden;
import com.ioteste.core.domain.Salida;
import com.ioteste.core.domain.TarifaPunta;
import com.ioteste.core.service.EvaluadorCore;
import com.ioteste.engine.domain.EstadoSwitchResponse;
import com.ioteste.engine.domain.Habitacion;
import com.ioteste.engine.domain.InventarioDTO;
import com.ioteste.engine.domain.MensajeTermostato;
import com.ioteste.engine.repository.SitioRepository;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
public class MqttSubscriberService implements MqttCallback {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final SitioRepository sitioRepository;
    private final SwitchClientService switchClientService;
    private final EvaluadorCore evaluadorCore;

    private MqttClient client;

    public MqttSubscriberService(DataSource dataSource, SitioRepository sitioRepository, SwitchClientService switchClientService) {
        this.dataSource = dataSource;
        this.sitioRepository = sitioRepository;
        this.switchClientService = switchClientService;
        this.objectMapper = new ObjectMapper();
        this.evaluadorCore = new EvaluadorCore();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciarSuscripcion() {
        Thread hilo = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    System.out.println("Conectando al broker MQTT: " + brokerUrl);

                    client = new MqttClient(brokerUrl, MqttClient.generateClientId(), new MemoryPersistence());

                    MqttConnectOptions options = new MqttConnectOptions();
                    options.setAutomaticReconnect(true);
                    options.setCleanSession(true);
                    options.setConnectionTimeout(10);

                    client.setCallback(this);
                    client.connect(options);

                    InventarioDTO inventario = sitioRepository.obtenerInventario();

                    if (inventario == null) {
                        throw new IllegalStateException("No hay inventario disponible para suscribirse");
                    }

                    for (Habitacion habitacion : inventario.habitaciones()) {
                        client.subscribe(habitacion.topicTermostato(), 1);
                        System.out.println("Suscrito al tópico: " + habitacion.topicTermostato());
                    }

                    System.out.println("Suscripciones MQTT activas.");
                    break;

                } catch (Exception e) {
                    System.err.println("No se pudo iniciar MQTT: " + e.getMessage());

                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });

        hilo.setName("mqtt-subscriber");
        hilo.start();
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) {
        try {
            String payload = new String(mqttMessage.getPayload(), StandardCharsets.UTF_8);

            MensajeTermostato mensaje = objectMapper.readValue(payload, MensajeTermostato.class);

            registrarTemperatura(topic, mensaje);

            // Cada nueva temperatura desencadena una evaluación global.
            evaluarYEjecutar();

        } catch (Exception e) {
            System.err.println("Error procesando el tópico " + topic + ": " + e.getMessage());
        }
    }

    private void registrarTemperatura(String topic, MensajeTermostato mensaje) throws SQLException {

        String buscarHabitacion = "SELECT id FROM habitacion WHERE topic_termostato = ?";

        String insertarTemperatura =
                "INSERT INTO historico_temperatura "
                + "(habitacion_id, fecha_hora, temperatura_c) "
                + "VALUES (?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement buscar = conn.prepareStatement(buscarHabitacion)) {

            buscar.setString(1, topic);

            try (ResultSet rs = buscar.executeQuery()) {
                if (!rs.next()) {
                    System.err.println("No existe habitación para el tópico " + topic);
                    return;
                }

                String idHabitacion = rs.getString("id");

                try (PreparedStatement insertar = conn.prepareStatement(insertarTemperatura)) {

                    insertar.setString(1, idHabitacion);
                    insertar.setTimestamp(2, new Timestamp((long) (mensaje.ts() * 1000)));
                    insertar.setDouble(3, mensaje.tC());
                    insertar.executeUpdate();
                }
            }
        }

        System.out.println("Temperatura registrada: " + mensaje.tC() + " °C (" + topic + ")");
    }

    private void evaluarYEjecutar() {
        InventarioDTO inventario = sitioRepository.obtenerInventario();

        if (inventario == null || inventario.habitaciones().isEmpty()) {
            System.err.println("No hay inventario para evaluar.");
            return;
        }

        List<HabitacionDTO> habitacionesCore = new ArrayList<>();
        Map<String, Habitacion> habitacionesPorId = new HashMap<>();

        for (Habitacion habitacion : inventario.habitaciones()) {
            Double temperatura = obtenerUltimaTemperatura(habitacion.id());

            // No evaluamos un conjunto incompleto.
            if (temperatura == null) {
                System.out.println("Esperando la primera temperatura de " + habitacion.id());
                return;
            }

            EstadoSwitch estado = consultarEstadoCore(habitacion);

            habitacionesCore.add(new HabitacionDTO(
                    habitacion.id(),
                    habitacion.temperaturaEsperada(),
                    temperatura,
                    habitacion.potenciaKW(),
                    estado
            ));

            habitacionesPorId.put(habitacion.id(), habitacion);
        }

        TarifaPunta tarifaPunta = convertirTarifa(inventario);

        ZonedDateTime ahora = ZonedDateTime.now(ZoneId.of("America/Montevideo"));

        Salida salida = evaluadorCore.evaluarEstado(ahora, inventario.sitio().potenciaContratadaKW(), tarifaPunta, habitacionesCore);

        for (Orden orden : salida.ordenes()) {
            Habitacion habitacion = habitacionesPorId.get(orden.idHabitacion());

            if (habitacion == null) {
                System.err.println("No se encontró la habitación " + orden.idHabitacion());
                continue;
            }

            String accion = orden.accion().name();

            switchClientService.comandarSwitch(habitacion.urlSwitch(), habitacion.idSwitch(), accion);
        }
    }

    private Double obtenerUltimaTemperatura(String idHabitacion) {
        String sql =
                "SELECT temperatura_c FROM historico_temperatura "
                + "WHERE habitacion_id = ? "
                + "ORDER BY fecha_hora DESC, id DESC LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, idHabitacion);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("temperatura_c");
                }
            }

        } catch (SQLException e) {
            System.err.println("Error consultando temperatura de " + idHabitacion + ": " + e.getMessage());
        }

        return null;
    }

    private EstadoSwitch consultarEstadoCore(Habitacion habitacion) {
        try {
            EstadoSwitchResponse respuesta = switchClientService.consultarEstado(habitacion.urlSwitch(), habitacion.idSwitch());

            if (respuesta == null || respuesta.estado() == null) {
                return EstadoSwitch.DESCONOCIDO;
            }

            return switch (respuesta.estado().toUpperCase()) {
                case "ON" -> EstadoSwitch.ON;
                case "OFF" -> EstadoSwitch.OFF;
                default -> EstadoSwitch.DESCONOCIDO;
            };

        } catch (Exception e) {
            System.err.println("No se pudo consultar el switch " + habitacion.idSwitch() + ": " + e.getMessage());
            return EstadoSwitch.DESCONOCIDO;
        }
    }

    private TarifaPunta convertirTarifa(InventarioDTO inventario) {
        var punta = inventario.sitio().tarifa().punta();

        Set<DayOfWeek> dias = switch (punta.dias().toUpperCase()) {
            case "HABILES" -> Set.of(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY
            );
            case "TODOS" -> Set.of(DayOfWeek.values());
            default -> throw new IllegalArgumentException("Valor de tarifa.dias no válido: " + punta.dias());
        };

        return new TarifaPunta(LocalTime.parse(punta.desde()), LocalTime.parse(punta.hasta()), dias);
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.err.println("Se perdió la conexión MQTT: " + (cause == null ? "causa desconocida" : cause.getMessage()));
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Este cliente recibe mensajes; no publica órdenes MQTT.
    }
}
