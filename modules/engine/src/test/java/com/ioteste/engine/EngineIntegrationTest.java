package com.ioteste.engine;

import com.ioteste.engine.service.EvaluadorJobService;
import com.ioteste.engine.service.SwitchClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EngineIntegrationTest {

    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private PreparedStatement stmtSitio;
    @Mock private PreparedStatement stmtHabitaciones;
    @Mock private PreparedStatement stmtSwitchId;
    @Mock private ResultSet rsSitio;
    @Mock private ResultSet rsHabitaciones;
    @Mock private ResultSet rsSwitchId;
    @Mock private SwitchClientService switchClientService;
    @Mock private RestTemplate restTemplateMock;

    private EvaluadorJobService evaluadorJobService;
    private String switchIdSimulado = "sw-1";

    @BeforeEach
    void setUp() throws Exception {
        evaluadorJobService = new EvaluadorJobService(switchClientService, dataSource);
        ReflectionTestUtils.setField(evaluadorJobService, "simuladorUrlBase", "http://mock-url");
        ReflectionTestUtils.setField(evaluadorJobService, "restTemplate", restTemplateMock);
        
        lenient().when(restTemplateMock.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                 .thenReturn(ResponseEntity.ok("OFF"));

        lenient().when(dataSource.getConnection()).thenReturn(connection);
        
        lenient().when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = ((String) invocation.getArgument(0)).toLowerCase();
            if (sql.contains("where id = ?")) return stmtSwitchId; 
            if (sql.contains("sitio")) return stmtSitio;
            if (sql.contains("habitacion")) return stmtHabitaciones;
            return null;
        });

        lenient().when(stmtSitio.executeQuery()).thenReturn(rsSitio);
        lenient().when(rsSitio.next()).thenReturn(true, false);
        lenient().when(rsSitio.getTime("tarifa_desde")).thenReturn(Time.valueOf("00:00:00"));
        lenient().when(rsSitio.getTime("tarifa_hasta")).thenReturn(Time.valueOf("23:59:59"));
        lenient().when(rsSitio.getString("tarifa_dias")).thenReturn(LocalDate.now().getDayOfWeek().name());

        lenient().when(stmtHabitaciones.executeQuery()).thenReturn(rsHabitaciones);
        lenient().when(stmtSwitchId.executeQuery()).thenReturn(rsSwitchId);
        lenient().when(rsSwitchId.next()).thenReturn(true, false);
        lenient().when(rsSwitchId.getString("id_switch")).thenAnswer(inv -> switchIdSimulado);

        lenient().doAnswer(inv -> {
            String habId = inv.getArgument(1);
            if (habId != null) {
                switchIdSimulado = habId.replace("hab-", "sw-");
            }
            return null;
        }).when(stmtSwitchId).setString(eq(1), anyString());
    }

    @Test
    void caso1_ControlTemperatura_SinLimitacion_DebeApagarSiHaceCalor() throws Exception {
        when(rsSitio.getDouble("potencia_contratada_kw")).thenReturn(10.0);
        
        when(rsHabitaciones.next()).thenReturn(true, false);
        when(rsHabitaciones.getString("id")).thenReturn("hab-1");
        when(rsHabitaciones.getDouble("temperatura_esperada")).thenReturn(20.0);
        when(rsHabitaciones.getDouble("potencia_kw")).thenReturn(2.0);
        when(rsHabitaciones.getString("id_switch")).thenReturn("sw-1");
        when(rsHabitaciones.getObject("temp_actual")).thenReturn(25.0);
        when(rsHabitaciones.getDouble("temp_actual")).thenReturn(25.0);
        when(rsHabitaciones.getTimestamp(anyString())).thenReturn(Timestamp.from(Instant.now()));

        when(restTemplateMock.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                 .thenReturn(ResponseEntity.ok("ON"));

        evaluadorJobService.ejecutarCicloDeControl();

        verify(switchClientService, times(1)).comandarSwitch(anyString(), eq("sw-1"), eq("OFF"));
    }

    @Test
    void caso2_ControlTemperatura_ConLimitacion_NoDebeEncenderPorFaltaDePotencia() throws Exception {
        when(rsSitio.getDouble("potencia_contratada_kw")).thenReturn(1.0); 
        
        when(rsHabitaciones.next()).thenReturn(true, false);
        when(rsHabitaciones.getString("id")).thenReturn("hab-1");
        when(rsHabitaciones.getDouble("temperatura_esperada")).thenReturn(25.0);
        when(rsHabitaciones.getDouble("potencia_kw")).thenReturn(2.0); 
        when(rsHabitaciones.getString("id_switch")).thenReturn("sw-1");
        when(rsHabitaciones.getObject("temp_actual")).thenReturn(15.0);
        when(rsHabitaciones.getDouble("temp_actual")).thenReturn(15.0);
        when(rsHabitaciones.getTimestamp(anyString())).thenReturn(Timestamp.from(Instant.now()));

        when(restTemplateMock.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                 .thenReturn(ResponseEntity.ok("OFF"));

        evaluadorJobService.ejecutarCicloDeControl();

        verify(switchClientService, never()).comandarSwitch(anyString(), eq("sw-1"), eq("ON"));
    }

@Test
    void caso3_CasosBorde_CaidaREST_Y_TermostatoRoto() throws Exception {
        when(rsSitio.getDouble("potencia_contratada_kw")).thenReturn(10.0);
        
        when(rsHabitaciones.next()).thenReturn(true, false);
        when(rsHabitaciones.getString("id")).thenReturn("hab-1");
        when(rsHabitaciones.getDouble("temperatura_esperada")).thenReturn(22.0);
        when(rsHabitaciones.getDouble("potencia_kw")).thenReturn(2.0);
        when(rsHabitaciones.getString("id_switch")).thenReturn("sw-1");
        when(rsHabitaciones.getObject("temp_actual")).thenReturn(15.0);
        when(rsHabitaciones.getDouble("temp_actual")).thenReturn(15.0);
        when(rsHabitaciones.getTimestamp(anyString())).thenReturn(
                Timestamp.from(Instant.now().minus(15, ChronoUnit.MINUTES)));

        when(restTemplateMock.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                 .thenReturn(ResponseEntity.ok("ON"));

        doThrow(new RuntimeException("Simulacro Crítico"))
            .when(switchClientService).comandarSwitch(anyString(), anyString(), anyString());

        evaluadorJobService.ejecutarCicloDeControl();

        verify(switchClientService, times(1)).comandarSwitch(anyString(), eq("sw-1"), eq("OFF"));
    }

    @Test
    void caso4_EscenarioComplejo_MultiplesEstadosYSwitchCaido() throws Exception {
        when(rsSitio.getDouble("potencia_contratada_kw")).thenReturn(20.0);
        
        when(rsHabitaciones.next()).thenReturn(true, false);
        when(rsHabitaciones.getString("id")).thenReturn("hab-3");
        when(rsHabitaciones.getDouble("temperatura_esperada")).thenReturn(20.0);
        when(rsHabitaciones.getDouble("potencia_kw")).thenReturn(2.0);
        when(rsHabitaciones.getString("id_switch")).thenReturn("sw-3");
        when(rsHabitaciones.getObject("temp_actual")).thenReturn(24.0);
        when(rsHabitaciones.getDouble("temp_actual")).thenReturn(24.0);
        when(rsHabitaciones.getTimestamp(anyString())).thenReturn(Timestamp.from(Instant.now()));

        when(restTemplateMock.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                 .thenReturn(ResponseEntity.ok("ON"));

        doThrow(new RuntimeException("Timeout critico sw-3"))
            .when(switchClientService).comandarSwitch(anyString(), eq("sw-3"), anyString());

        evaluadorJobService.ejecutarCicloDeControl();

        verify(switchClientService, atLeastOnce()).comandarSwitch(anyString(), eq("sw-3"), eq("OFF"));
    }

    @Test
    void caso5_SitioVacio_SinHabitaciones_NoDebeColapsar() throws Exception {
        when(rsSitio.getDouble("potencia_contratada_kw")).thenReturn(10.0);
        when(rsHabitaciones.next()).thenReturn(false);

        evaluadorJobService.ejecutarCicloDeControl();

        verify(switchClientService, never()).comandarSwitch(anyString(), anyString(), anyString());
    }

    @Test
    void caso6_Idempotencia_NoEnviarComandosRedundantes() throws Exception {
        when(rsSitio.getDouble("potencia_contratada_kw")).thenReturn(10.0);
        
        when(rsHabitaciones.next()).thenReturn(true, false);
        when(rsHabitaciones.getString("id")).thenReturn("hab-1");
        when(rsHabitaciones.getDouble("temperatura_esperada")).thenReturn(22.0);
        when(rsHabitaciones.getDouble("potencia_kw")).thenReturn(2.0);
        when(rsHabitaciones.getString("id_switch")).thenReturn("sw-1");
        when(rsHabitaciones.getObject("temp_actual")).thenReturn(24.0);
        when(rsHabitaciones.getDouble("temp_actual")).thenReturn(24.0);
        when(rsHabitaciones.getTimestamp(anyString())).thenReturn(Timestamp.from(Instant.now()));
        
        evaluadorJobService.ejecutarCicloDeControl();

        verify(switchClientService, never()).comandarSwitch(anyString(), eq("sw-1"), eq("OFF"));
    }

    @Test
    void caso7_CambioConfiguracion_ApagarSiCambiaTemperaturaEsperada() throws Exception {
        when(rsSitio.getDouble("potencia_contratada_kw")).thenReturn(10.0);
        
        when(rsHabitaciones.next()).thenReturn(true, false);
        when(rsHabitaciones.getString("id")).thenReturn("hab-1");
        when(rsHabitaciones.getDouble("temperatura_esperada")).thenReturn(15.0); 
        when(rsHabitaciones.getDouble("potencia_kw")).thenReturn(2.0);
        when(rsHabitaciones.getString("id_switch")).thenReturn("sw-1");
        when(rsHabitaciones.getObject("temp_actual")).thenReturn(18.0); 
        when(rsHabitaciones.getDouble("temp_actual")).thenReturn(18.0);
        when(rsHabitaciones.getTimestamp(anyString())).thenReturn(Timestamp.from(Instant.now()));

        when(restTemplateMock.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                 .thenReturn(ResponseEntity.ok("ON"));

        evaluadorJobService.ejecutarCicloDeControl();

        verify(switchClientService, times(1)).comandarSwitch(anyString(), eq("sw-1"), eq("OFF"));
    }
}