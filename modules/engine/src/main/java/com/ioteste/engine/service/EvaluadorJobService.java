package com.ioteste.engine.service;

import com.ioteste.core.domain.EstadoSwitch;
import com.ioteste.core.domain.HabitacionDTO;
import com.ioteste.core.domain.Orden;
import com.ioteste.core.domain.Salida;
import com.ioteste.core.domain.TarifaPunta;
import com.ioteste.core.service.EvaluadorCore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

@Service
public class EvaluadorJobService {

    private final SwitchClientService switchClientService;
    private final DataSource dataSource;
    private final EvaluadorCore evaluadorCore;
    private final RestTemplate restTemplate;
    private final ThreadPoolTaskScheduler scheduler;

    private ScheduledFuture<?> tareaProgramada;
    private Double factorAceleracion = 1.0;
    
    private Instant inicioReal;
    private ZonedDateTime inicioSimulado;

    @Value("${simulador.url}")
    private String simuladorUrlBase;

    @Value("${simulador.api.key:}")
    private String simuladorApiKey;

    public EvaluadorJobService(SwitchClientService switchClientService, DataSource dataSource) {
        this.switchClientService = switchClientService;
        this.dataSource = dataSource;
        this.evaluadorCore = new EvaluadorCore();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.restTemplate = new RestTemplate(factory);

        this.scheduler = new ThreadPoolTaskScheduler();
        this.scheduler.setPoolSize(1);
        this.scheduler.setThreadNamePrefix("MotorEngine-");
        this.scheduler.initialize();
    }

    public synchronized void iniciarMotor(Double factor, String fechaHora) {
        this.factorAceleracion = (factor != null && factor > 0) ? factor : 1.0;
        this.inicioReal = Instant.now();
        
        try {
            this.inicioSimulado = ZonedDateTime.parse(fechaHora);
        } catch (Exception e) {
            this.inicioSimulado = ZonedDateTime.now(ZoneId.of("America/Montevideo"));
        }

        if (tareaProgramada != null && !tareaProgramada.isCancelled()) {
            tareaProgramada.cancel(false);
        }

        long intervaloRealMillis = (long) (30000 / this.factorAceleracion);
        if (intervaloRealMillis < 500) {
            intervaloRealMillis = 500;
        }

        tareaProgramada = scheduler.scheduleAtFixedRate(
                this::ejecutarCicloDeControl,
                Duration.ofMillis(intervaloRealMillis)
        );
    }

    public synchronized void detenerMotor() {
        if (tareaProgramada != null && !tareaProgramada.isCancelled()) {
            tareaProgramada.cancel(false);
        }
    }

    private ZonedDateTime obtenerRelojSimulado() {
        if (inicioReal == null || inicioSimulado == null) {
            return ZonedDateTime.now(ZoneId.of("America/Montevideo"));
        }
        long millisReales = Duration.between(inicioReal, Instant.now()).toMillis();
        long millisAcelerados = (long) (millisReales * factorAceleracion);
        return inicioSimulado.plus(millisAcelerados, ChronoUnit.MILLIS);
    }

    public void ejecutarCicloDeControl() {
        ZonedDateTime ahoraSimulado = obtenerRelojSimulado();
        System.out.println("⏳ Ciclo Core | Hora simulada: " + ahoraSimulado);

        try (Connection conn = dataSource.getConnection()) {
            
            // 1. OBTENER DATOS DEL SITIO Y LA TARIFA
            double potenciaContratada = 0.0;
            LocalTime tarifaDesde = LocalTime.of(0, 0);
            LocalTime tarifaHasta = LocalTime.of(0, 0);
            Set<DayOfWeek> diasTarifa = new HashSet<>();

            String querySitio = "SELECT potencia_contratada_kw, tarifa_desde, tarifa_hasta, tarifa_dias FROM sitio LIMIT 1";
                              
            try (PreparedStatement stmt = conn.prepareStatement(querySitio); 
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    potenciaContratada = rs.getDouble("potencia_contratada_kw");
                    tarifaDesde = rs.getTime("tarifa_desde").toLocalTime();
                    tarifaHasta = rs.getTime("tarifa_hasta").toLocalTime();
                    String diasStr = rs.getString("tarifa_dias");
                    
                    if (diasStr != null && !diasStr.isEmpty()) {
                        for (String dia : diasStr.split(",")) {
                            String d = dia.trim().toUpperCase();
                            switch (d) {
                                case "HABILES":
                                    diasTarifa.add(DayOfWeek.MONDAY);
                                    diasTarifa.add(DayOfWeek.TUESDAY);
                                    diasTarifa.add(DayOfWeek.WEDNESDAY);
                                    diasTarifa.add(DayOfWeek.THURSDAY);
                                    diasTarifa.add(DayOfWeek.FRIDAY);
                                    break;
                                case "FINES_DE_SEMANA":
                                case "FIN DE SEMANA":
                                    diasTarifa.add(DayOfWeek.SATURDAY);
                                    diasTarifa.add(DayOfWeek.SUNDAY);
                                    break;
                                case "TODOS":
                                    diasTarifa.add(DayOfWeek.MONDAY);
                                    diasTarifa.add(DayOfWeek.TUESDAY);
                                    diasTarifa.add(DayOfWeek.WEDNESDAY);
                                    diasTarifa.add(DayOfWeek.THURSDAY);
                                    diasTarifa.add(DayOfWeek.FRIDAY);
                                    diasTarifa.add(DayOfWeek.SATURDAY);
                                    diasTarifa.add(DayOfWeek.SUNDAY);
                                    break;
                                default:
                                    try {
                                        diasTarifa.add(DayOfWeek.valueOf(d));
                                    } catch (IllegalArgumentException e) { }
                                    break;
                            }
                        }
                    }
                }
            }

            TarifaPunta tarifa = new TarifaPunta(tarifaDesde, tarifaHasta, diasTarifa);

            // 2. OBTENER HABITACIONES Y ESTADOS
            List<HabitacionDTO> habitaciones = new ArrayList<>();
            String queryHabitaciones = 
                "SELECT h.id, h.temperatura_esperada, h.potencia_kw, h.id_switch, "
                + "(SELECT temperatura_c FROM historico_temperatura ht "
                + "WHERE ht.habitacion_id = h.id ORDER BY fecha_hora DESC LIMIT 1) as temp_actual, "
                + "(SELECT fecha_hora FROM historico_temperatura ht "
                + "WHERE ht.habitacion_id = h.id ORDER BY fecha_hora DESC LIMIT 1) as ultima_fecha "
                + "FROM habitacion h";

            try (PreparedStatement stmt = conn.prepareStatement(queryHabitaciones); 
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String idHab = rs.getString("id");
                    double tempEsperada = rs.getDouble("temperatura_esperada");
                    double potenciaKw = rs.getDouble("potencia_kw");
                    String idSwitch = rs.getString("id_switch");
                    
                    double tempActual = 99.0;
                    Object tempObj = rs.getObject("temp_actual");
                    Timestamp ultimaFecha = rs.getTimestamp("ultima_fecha");

                    if (tempObj != null && ultimaFecha != null) {
                        long segundosSimulados;
                        
                        // Traducción matemática perfecta del tiempo real del DB a la dimensión del simulador
                        if (inicioReal != null && inicioSimulado != null) {
                            long millisDesdeInicio = Duration.between(inicioReal, ultimaFecha.toInstant()).toMillis();
                            ZonedDateTime fechaSimuladaDelRegistro = inicioSimulado.plus(
                                    (long) (millisDesdeInicio * factorAceleracion), ChronoUnit.MILLIS);
                            segundosSimulados = Duration.between(fechaSimuladaDelRegistro, ahoraSimulado).getSeconds();
                        } else {
                            // Caso Test: sin iniciarMotor(), usamos el tiempo puro
                            segundosSimulados = Duration.between(ultimaFecha.toInstant(), ahoraSimulado.toInstant()).getSeconds();
                        }

                        if (Math.abs(segundosSimulados) <= 600) {
                            tempActual = rs.getDouble("temp_actual");
                        } else {
                            System.err.println("⚠️ Termostato [" + idHab + "] inactivo. Forzando temperatura crítica (99.0C).");
                            tempActual = 99.0;
                        }
                    }

                    EstadoSwitch estadoActual = EstadoSwitch.OFF; 
                    try {
                        HttpHeaders headers = new HttpHeaders();
                        headers.set("X-API-Key", simuladorApiKey);
                        HttpEntity<Void> getEntity = new HttpEntity<>(headers);
                        
                        String urlEstado = simuladorUrlBase + "/switches/" + idSwitch;
                        String respuesta = restTemplate.exchange(urlEstado, HttpMethod.GET, getEntity, String.class).getBody();
                        
                        if (respuesta != null && respuesta.contains("ON") && !respuesta.contains("DESCONOCIDO")) {
                            estadoActual = EstadoSwitch.ON;
                        }
                    } catch (Exception e) {
                        estadoActual = EstadoSwitch.ON; 
                    }

                    habitaciones.add(new HabitacionDTO(idHab, tempEsperada, tempActual, potenciaKw, estadoActual));
                }
            }

            System.out.println("🔍 RADIOGRAFÍA DEL MOTOR:");
            System.out.println("   Hora motor: " + ahoraSimulado.toLocalTime() + " | Punta: " + tarifaDesde + "-" + tarifaHasta + " | Potencia DB: " + potenciaContratada + "kW");
            for(HabitacionDTO h : habitaciones) {
                System.out.println("   -> Hab: " + h.id() + " | Temp Actual: " + h.temperaturaActual() + "C | Esperada: " + h.temperaturaEsperada() + "C | Switch: " + h.estadoActual());
            }

            // 3. LLAMAR AL EVALUADOR CORE
            Salida resultado = evaluadorCore.evaluarEstado(ahoraSimulado, potenciaContratada, tarifa, habitaciones);

            // 4. DISPARAR LAS ÓRDENES
            for (Orden orden : resultado.ordenes()) {
                try {
                    String accionStr = orden.accion().name();
                    String idSwitchFinal = null;
                    
                    String querySwitch = "SELECT id_switch FROM habitacion WHERE id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(querySwitch)) {
                        stmt.setString(1, orden.idHabitacion());
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                idSwitchFinal = rs.getString("id_switch");
                            }
                        }
                    }

                    if (idSwitchFinal != null) {
                        switchClientService.comandarSwitch(simuladorUrlBase, idSwitchFinal, accionStr);
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Tolerancia a fallos: Ignorando error al comandar " 
                            + orden.idHabitacion() + ". Continuando con el resto... (" + e.getMessage() + ")");
                }
            }

        } catch (Exception e) {
            System.err.println("Error crítico ejecutando EvaluadorJobService: " + e.getMessage());
        }
    }
}