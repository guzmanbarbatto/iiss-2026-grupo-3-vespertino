package com.ioteste.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ioteste.mcp.client.EngineClient;
import com.ioteste.mcp.client.ServicioExternoException;
import com.ioteste.mcp.client.SimuladorClient;
import com.ioteste.mcp.client.dto.EngineDtos.EstadoControl;
import com.ioteste.mcp.client.dto.EngineDtos.Habitacion;
import com.ioteste.mcp.client.dto.EngineDtos.Inventario;
import com.ioteste.mcp.client.dto.EngineDtos.Sitio;
import com.ioteste.mcp.client.dto.EngineDtos.Tarifa;
import com.ioteste.mcp.client.dto.EngineDtos.TarifaPunta;
import com.ioteste.mcp.client.dto.SimuladorDtos.EstadoSwitch;
import com.ioteste.mcp.client.dto.SimuladorDtos.RespuestaComando;
import com.ioteste.mcp.repository.TemperaturaRepository;
import com.ioteste.mcp.repository.TemperaturaRepository.Lectura;
import com.ioteste.mcp.tools.ToolDtos.EstadoHabitaciones;
import com.ioteste.mcp.tools.ToolDtos.HistorialTemperaturas;
import com.ioteste.mcp.tools.ToolDtos.ResultadoAccionSwitch;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EcoWarmToolsTest {

    private static final Instant T1 = Instant.parse("2026-10-01T09:00:00Z");

    @Mock private EngineClient engineClient;
    @Mock private SimuladorClient simuladorClient;
    @Mock private TemperaturaRepository temperaturaRepository;

    private EcoWarmTools tools;

    @BeforeEach
    void setUp() {
        tools = new EcoWarmTools(engineClient, simuladorClient, temperaturaRepository);

        Inventario inventario = new Inventario("2.0",
                new Sitio("sitio-1", "Casa", 5.0, new Tarifa(new TarifaPunta("18:00", "22:00", "HABILES"))),
                List.of(
                        new Habitacion("hab-1", "Cuarto 1", 20.0, 2.0, "ht-1", "ht-1/status/temperature:0", "sw-1", "u"),
                        new Habitacion("hab-2", "Cuarto 2", 22.0, 3.0, "ht-2", "ht-2/status/temperature:0", "sw-2", "u")));
        when(engineClient.obtenerSitio()).thenReturn(Optional.of(inventario));
        when(engineClient.obtenerControl()).thenReturn(new EstadoControl("DETENIDO", null, null, null));
    }

    @Test
    void obtenerSitioSinSitioCargadoDaErrorClaro() {
        when(engineClient.obtenerSitio()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tools.obtenerSitio())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tiene un sitio cargado");
    }

    @Test
    void estadoHabitacionesCombinaTemperaturasSwitchesYPotencia() {
        when(temperaturaRepository.ultimasLecturas())
                .thenReturn(Map.of("hab-1", new Lectura("hab-1", T1, 18.5)));
        when(simuladorClient.consultarSwitch("sw-1")).thenReturn(new EstadoSwitch("sw-1", "ON", null));
        when(simuladorClient.consultarSwitch("sw-2")).thenReturn(new EstadoSwitch("sw-2", "OFF", null));

        EstadoHabitaciones estado = tools.consultarEstadoHabitaciones(null);

        assertThat(estado.potenciaContratadaKW()).isEqualTo(5.0);
        assertThat(estado.potenciaEnUsoKW()).isEqualTo(2.0);
        assertThat(estado.potenciaDisponibleKW()).isEqualTo(3.0);
        assertThat(estado.habitaciones()).hasSize(2);

        var hab1 = estado.habitaciones().get(0);
        assertThat(hab1.temperaturaActual()).isEqualTo(18.5);
        assertThat(hab1.necesitaCalor()).isTrue();
        assertThat(hab1.estadoSwitch()).isEqualTo("ON");
        assertThat(hab1.ultimaLecturaEn()).isEqualTo("2026-10-01T09:00:00Z");

        var hab2 = estado.habitaciones().get(1);
        assertThat(hab2.temperaturaActual()).isNull();
        assertThat(hab2.necesitaCalor()).isNull();
        assertThat(estado.advertencias()).anyMatch(a -> a.contains("hab-2"));
    }

    @Test
    void estadoHabitacionesToleraFallasDeSwitchYBaseDeDatos() {
        when(temperaturaRepository.ultimasLecturas()).thenThrow(new ServicioExternoException("BD caída"));
        when(simuladorClient.consultarSwitch("sw-1")).thenThrow(new ServicioExternoException("simulador caído"));
        when(simuladorClient.consultarSwitch("sw-2")).thenReturn(new EstadoSwitch("sw-2", "ON", null));

        EstadoHabitaciones estado = tools.consultarEstadoHabitaciones(null);

        assertThat(estado.habitaciones().get(0).estadoSwitch()).isEqualTo(EcoWarmTools.SWITCH_NO_DISPONIBLE);
        assertThat(estado.potenciaEnUsoKW()).isEqualTo(3.0);
        assertThat(estado.advertencias()).contains("BD caída", "simulador caído");
    }

    @Test
    void estadoHabitacionesFiltraPorIdPeroCalculaPotenciaDeTodaLaCasa() {
        when(temperaturaRepository.ultimasLecturas()).thenReturn(Map.of());
        when(simuladorClient.consultarSwitch(anyString())).thenReturn(new EstadoSwitch("x", "ON", null));

        EstadoHabitaciones estado = tools.consultarEstadoHabitaciones("hab-2");

        assertThat(estado.habitaciones()).extracting("id").containsExactly("hab-2");
        assertThat(estado.potenciaEnUsoKW()).isEqualTo(5.0);
    }

    @Test
    void estadoHabitacionesConIdInexistenteListaLasDisponibles() {
        assertThatThrownBy(() -> tools.consultarEstadoHabitaciones("hab-9"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hab-9")
                .hasMessageContaining("hab-1, hab-2");
    }

    @Test
    void historialUsaLimitePorDefecto() {
        when(temperaturaRepository.historial("hab-1", EcoWarmTools.LIMITE_HISTORIAL_POR_DEFECTO))
                .thenReturn(List.of(new Lectura("hab-1", T1, 19.0)));

        HistorialTemperaturas historial = tools.historialTemperaturas("hab-1", null);

        assertThat(historial.cantidad()).isEqualTo(1);
        assertThat(historial.lecturas().get(0).temperaturaC()).isEqualTo(19.0);
        assertThat(historial.temperaturaEsperada()).isEqualTo(20.0);
    }

    @Test
    void historialRechazaLimiteFueraDeRango() {
        assertThatThrownBy(() -> tools.historialTemperaturas("hab-1", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.historialTemperaturas("hab-1", 501))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void iniciarControlValidaFechaYFactor() {
        assertThatThrownBy(() -> tools.iniciarControl("ayer", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO-8601");
        assertThatThrownBy(() -> tools.iniciarControl("2026-10-01T09:15:00Z", 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("factorTiempo");
        verify(engineClient, never()).iniciarControl(any(), any());
    }

    @Test
    void iniciarControlDelegaEnElEngine() {
        EstadoControl enEjecucion = new EstadoControl("EN_EJECUCION", "2026-10-01T09:15:00Z", 10.0, "x");
        when(engineClient.iniciarControl("2026-10-01T09:15:00Z", 10.0)).thenReturn(enEjecucion);

        assertThat(tools.iniciarControl("2026-10-01T09:15:00Z", 10.0)).isEqualTo(enEjecucion);
    }

    @Test
    void accionarSwitchComandaElSwitchDeLaHabitacion() {
        when(simuladorClient.comandarSwitch("sw-2", true))
                .thenReturn(new RespuestaComando("sw-2", "ON", true, "2026-10-01T09:15:03.120Z"));

        ResultadoAccionSwitch r = tools.accionarSwitch("hab-2", true);

        assertThat(r.idSwitch()).isEqualTo("sw-2");
        assertThat(r.estado()).isEqualTo("ON");
        assertThat(r.cambio()).isTrue();
        assertThat(r.advertencia()).isNull();
    }

    @Test
    void accionarSwitchAdvierteSiElControlAutomaticoEstaCorriendo() {
        when(engineClient.obtenerControl()).thenReturn(new EstadoControl("EN_EJECUCION", "f", 1.0, "i"));
        when(simuladorClient.comandarSwitch("sw-1", false))
                .thenReturn(new RespuestaComando("sw-1", "OFF", false, "t"));

        ResultadoAccionSwitch r = tools.accionarSwitch("hab-1", false);

        assertThat(r.advertencia()).contains("EN_EJECUCION");
    }

    @Test
    void accionarSwitchConHabitacionInexistenteNoComanda() {
        assertThatThrownBy(() -> tools.accionarSwitch("hab-9", true))
                .isInstanceOf(IllegalArgumentException.class);
        verify(simuladorClient, never()).comandarSwitch(anyString(), anyBoolean());
    }
}

