package com.ioteste.core.service;

import com.ioteste.core.domain.AccionSwitch;
import com.ioteste.core.domain.EstadoSwitch;
import com.ioteste.core.domain.HabitacionDTO;
import com.ioteste.core.domain.Orden;
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

    @Test
    void casoTarifaNocturnaCruzaMedianocheDebeApagar() {
        EvaluadorCore evaluador = new EvaluadorCore();
        TarifaPunta tarifaNocturna = new TarifaPunta(
                LocalTime.of(22, 0),
                LocalTime.of(6, 0),
                Set.of(DayOfWeek.MONDAY)
        );
        ZonedDateTime lunes23hs = ZonedDateTime.of(2026, 10, 5, 23, 30, 0, 0, ZoneId.of("UTC"));
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 15.0, 2.0, EstadoSwitch.ON);

        Salida resultado = evaluador.evaluarEstado(lunes23hs, 10.0, tarifaNocturna, List.of(habA));

        assertEquals(1, resultado.ordenes().size());
        assertEquals(AccionSwitch.OFF, resultado.ordenes().get(0).accion());
    }

    // 3. TESTS DE TEMPERATURA Y SPRINT 5 (TERMOSTATO ROTO / FAIL-SAFE)
    @Test
    void casoFallaTermostatoTemperaturaDeEmergenciaDebeApagar() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime lunes10hs = ZonedDateTime.of(2026, 10, 5, 10, 0, 0, 0, ZoneId.of("UTC"));

        // El Engine envía 99.0 cuando no tiene lecturas o pierde conexión con el termostato.
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

    @Test
    void casoPotenciaLiberadaAlApagarSeAprovechaSinImportarElOrden() {
        EvaluadorCore evaluador = new EvaluadorCore();
        ZonedDateTime martes10hs = ZonedDateTime.of(2026, 10, 6, 10, 0, 0, 0, ZoneId.of("UTC"));

        // habA está primera en la lista, apagada y con frío (quiere 2 kW)
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 16.0, 2.0, EstadoSwitch.OFF);
        // habB está segunda, encendida pero ya superó la temperatura (va a liberar sus 2 kW)
        HabitacionDTO habB = new HabitacionDTO("B", 20.0, 23.0, 2.0, EstadoSwitch.ON);

        Salida resultado = evaluador.evaluarEstado(martes10hs, 2.0, tarifaHabiles, List.of(habA, habB));

        assertEquals(2, resultado.ordenes().size());
        assertEquals("B", resultado.ordenes().get(0).idHabitacion());
        assertEquals(AccionSwitch.OFF, resultado.ordenes().get(0).accion());
        assertEquals("A", resultado.ordenes().get(1).idHabitacion());
        assertEquals(AccionSwitch.ON, resultado.ordenes().get(1).accion());
    }

    // 6. TESTS DE SWITCH EN ESTADO DESCONOCIDO (no se supone nada: se comanda explícitamente)
    private final ZonedDateTime martes10hs = ZonedDateTime.of(2026, 10, 6, 10, 0, 0, 0, ZoneId.of("UTC"));
    private final ZonedDateTime lunes18hs = ZonedDateTime.of(2026, 10, 5, 18, 0, 0, 0, ZoneId.of("UTC"));

    @Test
    void casoDesconocidoEnTarifaPuntaDebeApagar() {
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 15.0, 2.0, EstadoSwitch.DESCONOCIDO);

        Salida resultado = new EvaluadorCore().evaluarEstado(lunes18hs, 10.0, tarifaHabiles, List.of(habA));

        assertEquals(List.of(new Orden("A", AccionSwitch.OFF)), resultado.ordenes());
    }

    @Test
    void casoDesconocidoSinNecesidadDeCalorDebeApagar() {
        HabitacionDTO habA = new HabitacionDTO("A", 20.0, 20.2, 2.0, EstadoSwitch.DESCONOCIDO);

        Salida resultado = new EvaluadorCore().evaluarEstado(martes10hs, 10.0, tarifaHabiles, List.of(habA));

        assertEquals(List.of(new Orden("A", AccionSwitch.OFF)), resultado.ordenes());
    }

    @Test
    void casoDesconocidoConFrioYPotenciaDebeEncender() {
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 15.0, 2.0, EstadoSwitch.DESCONOCIDO);

        Salida resultado = new EvaluadorCore().evaluarEstado(martes10hs, 10.0, tarifaHabiles, List.of(habA));

        assertEquals(List.of(new Orden("A", AccionSwitch.ON)), resultado.ordenes());
    }

    @Test
    void casoDesconocidoConFrioSinPotenciaDebeApagarParaNoSuperarElTope() {
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 15.0, 3.0, EstadoSwitch.ON);
        HabitacionDTO habB = new HabitacionDTO("B", 22.0, 15.0, 2.0, EstadoSwitch.DESCONOCIDO);

        Salida resultado = new EvaluadorCore().evaluarEstado(martes10hs, 4.0, tarifaHabiles, List.of(habA, habB));

        assertEquals(List.of(new Orden("B", AccionSwitch.OFF)), resultado.ordenes());
    }

    // 7. TESTS DE HISTÉRESIS (evita prender y apagar el relé en cada ciclo)
    @Test
    void casoEncendidaDentroDelMargenDeHisteresisSigueEncendida() {
        HabitacionDTO habA = new HabitacionDTO("A", 20.0, 20.3, 2.0, EstadoSwitch.ON);

        Salida resultado = new EvaluadorCore().evaluarEstado(martes10hs, 10.0, tarifaHabiles, List.of(habA));

        assertTrue(resultado.ordenes().isEmpty());
    }

    @Test
    void casoEncendidaQueSuperaElMargenDeHisteresisSeApaga() {
        double limite = 20.0 + EvaluadorCore.HISTERESIS_C;
        HabitacionDTO habA = new HabitacionDTO("A", 20.0, limite, 2.0, EstadoSwitch.ON);

        Salida resultado = new EvaluadorCore().evaluarEstado(martes10hs, 10.0, tarifaHabiles, List.of(habA));

        assertEquals(List.of(new Orden("A", AccionSwitch.OFF)), resultado.ordenes());
    }

    @Test
    void casoApagadaDentroDelMargenNoSeEnciende() {
        HabitacionDTO habA = new HabitacionDTO("A", 20.0, 20.0, 2.0, EstadoSwitch.OFF);

        Salida resultado = new EvaluadorCore().evaluarEstado(martes10hs, 10.0, tarifaHabiles, List.of(habA));

        assertTrue(resultado.ordenes().isEmpty());
    }

    // 8. TESTS DE PRIORIDAD (con potencia escasa, primero la habitación más lejos de su objetivo)
    @Test
    void casoPotenciaEscasaPriorizaLaHabitacionMasFriaSinImportarElOrden() {
        HabitacionDTO habA = new HabitacionDTO("A", 20.0, 19.0, 2.0, EstadoSwitch.OFF);
        HabitacionDTO habB = new HabitacionDTO("B", 20.0, 12.0, 2.0, EstadoSwitch.OFF);

        Salida resultado = new EvaluadorCore().evaluarEstado(martes10hs, 2.0, tarifaHabiles, List.of(habA, habB));

        assertEquals(List.of(new Orden("B", AccionSwitch.ON)), resultado.ordenes());
    }

    @Test
    void casoLasOrdenesDeApagadoVanAntesQueLasDeEncendido() {
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 15.0, 1.0, EstadoSwitch.OFF);
        HabitacionDTO habB = new HabitacionDTO("B", 20.0, 25.0, 1.0, EstadoSwitch.ON);
        HabitacionDTO habC = new HabitacionDTO("C", 22.0, 15.0, 1.0, EstadoSwitch.OFF);

        Salida resultado = new EvaluadorCore().evaluarEstado(
                martes10hs, 10.0, tarifaHabiles, List.of(habA, habB, habC));

        assertEquals(List.of(
                new Orden("B", AccionSwitch.OFF),
                new Orden("A", AccionSwitch.ON),
                new Orden("C", AccionSwitch.ON)), resultado.ordenes());
    }

    @Test
    void casoFranjaVaciaNuncaEsPunta() {
        TarifaPunta vacia = new TarifaPunta(LocalTime.of(17, 0), LocalTime.of(17, 0), Set.of(DayOfWeek.MONDAY));
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 15.0, 2.0, EstadoSwitch.OFF);

        Salida resultado = new EvaluadorCore().evaluarEstado(
                lunes18hs.withHour(17), 10.0, vacia, List.of(habA));

        assertEquals(List.of(new Orden("A", AccionSwitch.ON)), resultado.ordenes());
    }
}
