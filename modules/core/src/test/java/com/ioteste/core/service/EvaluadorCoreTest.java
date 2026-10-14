package com.ioteste.core.service;

import com.ioteste.core.domain.*;
import org.junit.jupiter.api.Test;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class EvaluadorCoreTest {

    // Tarifa Punta estándar: Lunes a Viernes de 17:00 a 23:00
    private final TarifaPunta tarifaHabiles = new TarifaPunta(
            LocalTime.of(17, 0), LocalTime.of(23, 0),
            Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    );

    // ---------------------------------------------------------
    // 1. TESTS DE TARIFA PUNTA (REGLA 1)
    // ---------------------------------------------------------

    @Test
    void caso_Lunes18hs_DentroDeTarifaPunta_DebeApagar() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime lunes18hs = ZonedDateTime.of(2026, 10, 5, 18, 0, 0, 0, ZoneId.of("UTC"));
        
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 21.0, 2.0, EstadoSwitch.ON);

        Salida resultado = evaluador.evaluarEstado(lunes18hs, 10.0, tarifaHabiles, List.of(habA));

        assertEquals(1, resultado.ordenes().size());
        assertEquals(AccionSwitch.OFF, resultado.ordenes().get(0).accion());
    }

    @Test
    void caso_FinDeSemana18hs_TarifaPuntaNoAplica_DebeEncender() {
        EvaluadorCore evaluador = new EvaluadorCore();
        // Domingo 4 de Octubre de 2026 a las 18:00 (misma hora que la tarifa, pero es domingo)
        ZonedDateTime domingo18hs = ZonedDateTime.of(2026, 10, 4, 18, 0, 0, 0, ZoneId.of("UTC"));
        
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 15.0, 2.0, EstadoSwitch.OFF);

        Salida resultado = evaluador.evaluarEstado(domingo18hs, 10.0, tarifaHabiles, List.of(habA));

        // Como es fin de semana, la regla de punta no aplica y puede encenderse
        assertEquals(1, resultado.ordenes().size());
        assertEquals(AccionSwitch.ON, resultado.ordenes().get(0).accion());
    }

    // ---------------------------------------------------------
    // 2. TESTS DE LÍMITES DE HORA EXACTOS (CASOS BORDE)
    // ---------------------------------------------------------

    @Test
    void caso_MinutoExactoInicioTarifaPunta_DebeApagar() {
        EvaluadorCore evaluador = new EvaluadorCore();
        // Lunes 17:00 exacto. La tarifa punta es inclusiva en el inicio [desde, hasta)
        ZonedDateTime lunes17hs = ZonedDateTime.of(2026, 10, 5, 17, 0, 0, 0, ZoneId.of("UTC"));
        
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 15.0, 2.0, EstadoSwitch.ON);

        Salida resultado = evaluador.evaluarEstado(lunes17hs, 10.0, tarifaHabiles, List.of(habA));

        assertEquals(1, resultado.ordenes().size());
        assertEquals(AccionSwitch.OFF, resultado.ordenes().get(0).accion());
    }

    @Test
    void caso_MinutoExactoFinTarifaPunta_DebePermitirEncender() {
        EvaluadorCore evaluador = new EvaluadorCore();
        // Lunes 23:00 exacto. La tarifa punta es exclusiva en el fin [desde, hasta)
        ZonedDateTime lunes23hs = ZonedDateTime.of(2026, 10, 5, 23, 0, 0, 0, ZoneId.of("UTC"));
        
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 15.0, 2.0, EstadoSwitch.OFF);

        Salida resultado = evaluador.evaluarEstado(lunes23hs, 10.0, tarifaHabiles, List.of(habA));

        // A las 23:00 ya terminó la restricción, por lo que puede encender
        assertEquals(1, resultado.ordenes().size());
        assertEquals(AccionSwitch.ON, resultado.ordenes().get(0).accion());
    }

    // ---------------------------------------------------------
    // 3. TESTS DE TEMPERATURA (REGLA 2 Y 3)
    // ---------------------------------------------------------

    @Test
    void caso_NecesitaCalor_PotenciaSuficiente_DebeEncender() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime lunes10hs = ZonedDateTime.of(2026, 10, 5, 10, 0, 0, 0, ZoneId.of("UTC"));
        
        HabitacionDTO habA = new HabitacionDTO("A", 25.0, 18.0, 2.0, EstadoSwitch.OFF);

        Salida resultado = evaluador.evaluarEstado(lunes10hs, 10.0, tarifaHabiles, List.of(habA));

        assertEquals(1, resultado.ordenes().size());
        assertEquals(AccionSwitch.ON, resultado.ordenes().get(0).accion());
    }

    @Test
    void caso_TemperaturaAlcanzada_DebeApagar() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime lunes10hs = ZonedDateTime.of(2026, 10, 5, 10, 0, 0, 0, ZoneId.of("UTC"));
        
        // Esperada: 20.0, Actual: 21.0 -> Ya está caliente
        HabitacionDTO habA = new HabitacionDTO("A", 20.0, 21.0, 2.0, EstadoSwitch.ON);

        Salida resultado = evaluador.evaluarEstado(lunes10hs, 10.0, tarifaHabiles, List.of(habA));

        assertEquals(1, resultado.ordenes().size());
        assertEquals(AccionSwitch.OFF, resultado.ordenes().get(0).accion());
    }

    // ---------------------------------------------------------
    // 4. TESTS DE PREVENCIÓN DE COMANDOS DUPLICADOS (IDEMPOTENCIA)
    // ---------------------------------------------------------

    @Test
    void caso_YaEstaEncendida_Y_NecesitaCalor_NoHaceNada() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime lunes10hs = ZonedDateTime.of(2026, 10, 5, 10, 0, 0, 0, ZoneId.of("UTC"));
        
        // Ya está ON y sigue necesitando calor. No deberíamos enviar otro ON para no saturar la red.
        HabitacionDTO habA = new HabitacionDTO("A", 25.0, 18.0, 2.0, EstadoSwitch.ON);

        Salida resultado = evaluador.evaluarEstado(lunes10hs, 10.0, tarifaHabiles, List.of(habA));

        assertTrue(resultado.ordenes().isEmpty());
    }

    @Test
    void caso_YaEstaApagada_Y_TemperaturaAlcanzada_NoHaceNada() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime lunes10hs = ZonedDateTime.of(2026, 10, 5, 10, 0, 0, 0, ZoneId.of("UTC"));
        
        // Ya está OFF y no necesita calor. No deberíamos enviar otro OFF.
        HabitacionDTO habA = new HabitacionDTO("A", 20.0, 21.0, 2.0, EstadoSwitch.OFF);

        Salida resultado = evaluador.evaluarEstado(lunes10hs, 10.0, tarifaHabiles, List.of(habA));

        assertTrue(resultado.ordenes().isEmpty());
    }

    // ---------------------------------------------------------
    // 5. TESTS DE LÍMITE DE POTENCIA ELÉCTRICA
    // ---------------------------------------------------------

    @Test
    void caso_LimitePotencia_SiSuperaMaximo_NoDebeEncenderSegundaHabitacion() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime martes10hs = ZonedDateTime.of(2026, 10, 6, 10, 0, 0, 0, ZoneId.of("UTC"));
        
        // Potencia max: 4.0 kW. A ocupa 3.0. B ocupa 2.0.
        HabitacionDTO habA = new HabitacionDTO("A", 25.0, 18.0, 3.0, EstadoSwitch.ON);
        HabitacionDTO habB = new HabitacionDTO("B", 25.0, 18.0, 2.0, EstadoSwitch.OFF);

        Salida resultado = evaluador.evaluarEstado(martes10hs, 4.0, tarifaHabiles, List.of(habA, habB));

        // B se tiene que quedar apagada para no volar la llave térmica (3.0 + 2.0 > 4.0)
        assertTrue(resultado.ordenes().isEmpty());
    }

    @Test
    void caso_MultiplesHabitaciones_EncendidoParcialPorPotencia() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime martes10hs = ZonedDateTime.of(2026, 10, 6, 10, 0, 0, 0, ZoneId.of("UTC"));
        
        // Potencia max: 3.5 kW. 
        // Todas necesitan calor y están OFF.
        HabitacionDTO habA = new HabitacionDTO("A", 25.0, 15.0, 2.0, EstadoSwitch.OFF);
        HabitacionDTO habB = new HabitacionDTO("B", 25.0, 15.0, 1.0, EstadoSwitch.OFF);
        HabitacionDTO habC = new HabitacionDTO("C", 25.0, 15.0, 1.0, EstadoSwitch.OFF);

        Salida resultado = evaluador.evaluarEstado(martes10hs, 3.5, tarifaHabiles, List.of(habA, habB, habC));

        // A (2.0) y B (1.0) se encienden sumando 3.0 kW. 
        // C (1.0) intenta encender, pero 3.0 + 1.0 > 3.5, así que C queda descartada.
        assertEquals(2, resultado.ordenes().size());
        assertEquals("A", resultado.ordenes().get(0).idHabitacion());
        assertEquals("B", resultado.ordenes().get(1).idHabitacion());
    }
}