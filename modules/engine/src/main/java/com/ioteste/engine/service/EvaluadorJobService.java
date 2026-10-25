package com.ioteste.engine.service;
import com.ioteste.core.domain.*;
import com.ioteste.core.service.EvaluadorCore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

@Service
public class EvaluadorJobService {

    private final SwitchClientService switchClientService;
    private final DataSource dataSource;
    private final EvaluadorCore evaluadorCore;
    private final RestTemplate restTemplate;

    @Value("${simulador.url}")
    private String simuladorUrlBase;

    @Value("${simulador.api.key:}")
    private String simuladorApiKey;

    public EvaluadorJobService(SwitchClientService switchClientService, DataSource dataSource) {
        this.switchClientService = switchClientService;
        this.dataSource = dataSource;
        this.evaluadorCore = new EvaluadorCore();
        this.restTemplate = new RestTemplate();
    }

    // Se ejecuta automáticamente cada 30 segundos
    @Scheduled(fixedRate = 30000)
    public void ejecutarCicloDeControl() {
        System.out.println("⏳ Ejecutando ciclo de evaluación del Core...");

        try (Connection conn = dataSource.getConnection()) {
            
            // 1. OBTENER DATOS DEL SITIO Y LA TARIFA DESDE LA BASE DE DATOS
            double potenciaContratada = 0.0;
            LocalTime tarifaDesde = LocalTime.of(0, 0);
            LocalTime tarifaHasta = LocalTime.of(0, 0);
            Set<DayOfWeek> diasTarifa = new HashSet<>();

            String querySitio = "SELECT potencia_contratada_kw, tarifa_desde, tarifa_hasta, tarifa_dias FROM sitio LIMIT 1";
            try (PreparedStatement stmt = conn.prepareStatement(querySitio); ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    potenciaContratada = rs.getDouble("potencia_contratada_kw");
                    tarifaDesde = rs.getTime("tarifa_desde").toLocalTime();
                    tarifaHasta = rs.getTime("tarifa_hasta").toLocalTime();
                    String diasStr = rs.getString("tarifa_dias");
                    
                    // Parsear los días mapeando el vocabulario del simulador
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
                                    } catch (IllegalArgumentException e) {
                                        System.err.println("Advertencia: No se pudo mapear el día de la tarifa: " + d);
                                    }
                                    break;
                            }
                        }
                    }
                }
            }

            TarifaPunta tarifa = new TarifaPunta(tarifaDesde, tarifaHasta, diasTarifa);

            // 2. OBTENER HABITACIONES, ÚLTIMA TEMPERATURA Y ESTADO DEL SWITCH
            List<HabitacionDTO> habitaciones = new ArrayList<>();
            String queryHabitaciones = 
                "SELECT h.id, h.temperatura_esperada, h.potencia_kw, h.id_switch, h.url_switch, " +
                "(SELECT temperatura_c FROM historico_temperatura ht WHERE ht.habitacion_id = h.id ORDER BY fecha_hora DESC LIMIT 1) as temp_actual " +
                "FROM habitacion h";

            try (PreparedStatement stmt = conn.prepareStatement(queryHabitaciones); ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String idHab = rs.getString("id");
                    double tempEsperada = rs.getDouble("temperatura_esperada");
                    double potenciaKw = rs.getDouble("potencia_kw");
                    String idSwitch = rs.getString("id_switch");
                    
                    // Si no hay histórico aún, le ponemos 99.0 para que el core no lo encienda por error
                    double tempActual = rs.getObject("temp_actual") != null ? rs.getDouble("temp_actual") : 99.0;

                    // Consultar el estado real del switch pegándole a la API del simulador
                    EstadoSwitch estadoActual = EstadoSwitch.OFF; 
                    try {
                        HttpHeaders headers = new HttpHeaders();
                        headers.set("X-API-Key", simuladorApiKey);
                        HttpEntity<Void> getEntity = new HttpEntity<>(headers);
                        
                        String urlEstado = simuladorUrlBase + "/switches/" + idSwitch;
                        String respuesta = restTemplate.exchange(urlEstado, HttpMethod.GET, getEntity, String.class).getBody();
                        
                        if (respuesta != null && respuesta.contains("ON")) {
                            estadoActual = EstadoSwitch.ON;
                        }
                    } catch (Exception e) {
                        System.err.println("Advertencia: No se pudo consultar el estado actual del switch " + idSwitch);
                    }

                    habitaciones.add(new HabitacionDTO(idHab, tempActual, tempEsperada, potenciaKw, estadoActual));
                }
            }

            // 3. LLAMAR AL EVALUADOR CORE
            ZonedDateTime ahora = ZonedDateTime.now(ZoneId.of("America/Montevideo"));
            Salida resultado = evaluadorCore.evaluarEstado(ahora, potenciaContratada, tarifa, habitaciones);

            // 4. DISPARAR LAS ÓRDENES INDICADAS POR EL CORE
            for (Orden orden : resultado.ordenes()) {
                String accionStr = orden.accion().name();
                
                // Buscar el id_switch correspondiente al ID de la habitacion que devolvió el Core
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
            }

        } catch (Exception e) {
            System.err.println("Error crítico ejecutando EvaluadorJobService: " + e.getMessage());
            e.printStackTrace();
        }
    }
}