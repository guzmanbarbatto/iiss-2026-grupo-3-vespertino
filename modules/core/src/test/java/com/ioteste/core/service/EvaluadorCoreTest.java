package com.ioteste.core.service;

import com.ioteste.core.domain.AccionSwitch;
import com.ioteste.core.domain.EstadoSwitch;
import com.ioteste.core.domain.HabitacionDTO;
import com.ioteste.core.domain.Salida;
import com.ioteste.core.domain.TarifaPunta;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EvaluadorCoreTest {

    // Tarifa Punta estándar: Lunes a Viernes de 17:00 a 23:00
    private final TarifaPunta tarifaHabiles = new TarifaPunta(
            LocalTime.of(17, 0),
            LocalTime.of(23, 0),
            Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    );

    // 1. TESTS DE TARIFA PUNTA (REGLA 1)
    @Test
    void casoLunes18hsDentroDeTarifaPuntaDebeApagar() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime lunes18hs = ZonedDateTime.of(2026, 10, 5, 18, 0, 0, 0, ZoneId.of("UTC"));
        
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 21.0, 2.0, EstadoSwitch.ON);

        Salida resultado = evaluador.evaluarEstado(lunes18hs, 10.0, tarifaHabiles, List.of(habA));

        assertEquals(1, resultado.ordenes().size());
        assertEquals(AccionSwitch.OFF, resultado.ordenes().get(0).accion());
    }

    @Test
    void casoFinDeSemana18hsTarifaPuntaNoAplicaDebeEncender() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime domingo18hs = ZonedDateTime.of(2026, 10, 4, 18, 0, 0, 0, ZoneId.of("UTC"));
        
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 15.0, 2.0, EstadoSwitch.OFF);

        Salida resultado = evaluador.evaluarEstado(domingo18hs, 10.0, tarifaHabiles, List.of(habA));

        assertEquals(1, resultado.ordenes().size());
        assertEquals(AccionSwitch.ON, resultado.ordenes().get(0).accion());
    }

    // 2. TESTS DE LÍMITES DE HORA EXACTOS (CASOS BORDE)
    @Test
    void casoMinutoExactoInicioTarifaPuntaDebeApagar() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime lunes17hs = ZonedDateTime.of(2026, 10, 5, 17, 0, 0, 0, ZoneId.of("UTC"));
        
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 15.0, 2.0, EstadoSwitch.ON);

        Salida resultado = evaluador.evaluarEstado(lunes17hs, 10.0, tarifaHabiles, List.of(habA));

        assertEquals(1, resultado.ordenes().size());
        assertEquals(AccionSwitch.OFF, resultado.ordenes().get(0).accion());
    }

    @Test
    void casoMinutoExactoFinTarifaPuntaDebePermitirEncender() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime lunes23hs = ZonedDateTime.of(2026, 10, 5, 23, 0, 0, 0, ZoneId.of("UTC"));
        
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 15.0, 2.0, EstadoSwitch.OFF);

        Salida resultado = evaluador.evaluarEstado(lunes23hs, 10.0, tarifaHabiles, List.of(habA));

        assertEquals(1, resultado.ordenes().size());
        assertEquals(AccionSwitch.ON, resultado.ordenes().get(0).accion());
    }

    // 3. TESTS DE TEMPERATURA Y SPRINT 5 (TERMOSTATO ROTO)
    @Test
    void casoFallaTermostatoTemperaturaDeEmergenciaDebeApagar() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime lunes10hs = ZonedDateTime.of(2026, 10, 5, 10, 0, 0, 0, ZoneId.of("UTC"));
        
        // El Engine envía 99.0 cuando pierde conexión con el termostato.
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 99.0, 2.0, EstadoSwitch.ON);

        Salida resultado = evaluador.evaluarEstado(lunes10hs, 10.0, tarifaHabiles, List.of(habA));

        assertEquals(1, resultado.ordenes().size());
        assertEquals(AccionSwitch.OFF, resultado.ordenes().get(0).accion());
    }

    @Test
    void casoNecesitaCalorPotenciaSuficienteDebeEncender() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime lunes10hs = ZonedDateTime.of(2026, 10, 5, 10, 0, 0, 0, ZoneId.of("UTC"));
        
        HabitacionDTO habA = new HabitacionDTO("A", 25.0, 18.0, 2.0, EstadoSwitch.OFF);

        Salida resultado = evaluador.evaluarEstado(lunes10hs, 10.0, tarifaHabiles, List.of(habA));

        assertEquals(1, resultado.ordenes().size());
        assertEquals(AccionSwitch.ON, resultado.ordenes().get(0).accion());
    }

    @Test
    void casoTemperaturaAlcanzadaDebeApagar() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime lunes10hs = ZonedDateTime.of(2026, 10, 5, 10, 0, 0, 0, ZoneId.of("UTC"));
        
        HabitacionDTO habA = new HabitacionDTO("A", 20.0, 21.0, 2.0, EstadoSwitch.ON);

        Salida resultado = evaluador.evaluarEstado(lunes10hs, 10.0, tarifaHabiles, List.of(habA));

        assertEquals(1, resultado.ordenes().size());
        assertEquals(AccionSwitch.OFF, resultado.ordenes().get(0).accion());
    }

    // 4. TESTS DE PREVENCIÓN DE COMANDOS DUPLICADOS (IDEMPOTENCIA)
    @Test
    void casoYaEstaEncendidaYNecesitaCalorNoHaceNada() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime lunes10hs = ZonedDateTime.of(2026, 10, 5, 10, 0, 0, 0, ZoneId.of("UTC"));
        
        HabitacionDTO habA = new HabitacionDTO("A", 25.0, 18.0, 2.0, EstadoSwitch.ON);

        Salida resultado = evaluador.evaluarEstado(lunes10hs, 10.0, tarifaHabiles, List.of(habA));

        assertTrue(resultado.ordenes().isEmpty());
    }

    @Test
    void casoYaEstaApagadaYTemperaturaAlcanzadaNoHaceNada() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime lunes10hs = ZonedDateTime.of(2026, 10, 5, 10, 0, 0, 0, ZoneId.of("UTC"));
        
        HabitacionDTO habA = new HabitacionDTO("A", 20.0, 21.0, 2.0, EstadoSwitch.OFF);

        Salida resultado = evaluador.evaluarEstado(lunes10hs, 10.0, tarifaHabiles, List.of(habA));

        assertTrue(resultado.ordenes().isEmpty());
    }

    // 5. TESTS DE LÍMITE DE POTENCIA ELÉCTRICA
    @Test
    void casoLimitePotenciaSiSuperaMaximoNoDebeEncenderSegundaHabitacion() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime martes10hs = ZonedDateTime.of(2026, 10, 6, 10, 0, 0, 0, ZoneId.of("UTC"));
        
        HabitacionDTO habA = new HabitacionDTO("A", 25.0, 18.0, 3.0, EstadoSwitch.ON);
        HabitacionDTO habB = new HabitacionDTO("B", 25.0, 18.0, 2.0, EstadoSwitch.OFF);

        Salida resultado = evaluador.evaluarEstado(martes10hs, 4.0, tarifaHabiles, List.of(habA, habB));

        assertTrue(resultado.ordenes().isEmpty());
    }

    @Test
    void casoMultiplesHabitacionesEncendidoParcialPorPotencia() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime martes10hs = ZonedDateTime.of(2026, 10, 6, 10, 0, 0, 0, ZoneId.of("UTC"));
        
        HabitacionDTO habA = new HabitacionDTO("A", 25.0, 15.0, 2.0, EstadoSwitch.OFF);
        HabitacionDTO habB = new HabitacionDTO("B", 25.0, 15.0, 1.0, EstadoSwitch.OFF);
        HabitacionDTO habC = new HabitacionDTO("C", 25.0, 15.0, 1.0, EstadoSwitch.OFF);

        Salida resultado = evaluador.evaluarEstado(martes10hs, 3.5, tarifaHabiles, List.of(habA, habB, habC));

        assertEquals(2, resultado.ordenes().size());
        assertEquals("A", resultado.ordenes().get(0).idHabitacion());
        assertEquals("B", resultado.ordenes().get(1).idHabitacion());
    }
}