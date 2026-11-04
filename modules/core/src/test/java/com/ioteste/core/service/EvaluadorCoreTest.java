package com.ioteste.core.service;

import com.ioteste.core.domain.AccionSwitch;
import com.ioteste.core.domain.EstadoSwitch;
import com.ioteste.core.domain.HabitacionDTO;
import com.ioteste.core.domain.Orden;
import com.ioteste.core.domain.Salida;
import com.ioteste.core.domain.TarifaPunta;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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

    // 9. TESTS DE CONTRATADA QUE BAJA (lo encendido ya no entra: se apaga lo que sobra)
    @Test
    void casoBajaLaContratadaSeApagaLoQueSobraEmpezandoPorLaMasCercaDeSuObjetivo() {
        HabitacionDTO habA = new HabitacionDTO("A", 21.0, 18.0, 1.2, EstadoSwitch.ON);
        HabitacionDTO habB = new HabitacionDTO("B", 21.0, 20.8, 1.2, EstadoSwitch.ON);
        HabitacionDTO habC = new HabitacionDTO("C", 21.0, 19.0, 1.2, EstadoSwitch.ON);

        Salida resultado = new EvaluadorCore().evaluarEstado(
                martes10hs, 2.5, tarifaHabiles, List.of(habA, habB, habC));

        // 3,6 kW encendidos con 2,5 contratados: sale B (le faltan 0,2 °C); A y C (2,4 kW) entran
        assertEquals(List.of(new Orden("B", AccionSwitch.OFF)), resultado.ordenes());
    }

    @Test
    void casoBajaLaContratadaPorDebajoDeTodoSeApagaTodo() {
        HabitacionDTO habA = new HabitacionDTO("A", 21.0, 18.0, 2.0, EstadoSwitch.ON);
        HabitacionDTO habB = new HabitacionDTO("B", 21.0, 19.0, 2.0, EstadoSwitch.ON);

        Salida resultado = new EvaluadorCore().evaluarEstado(martes10hs, 1.5, tarifaHabiles, List.of(habA, habB));

        assertEquals(List.of(new Orden("B", AccionSwitch.OFF), new Orden("A", AccionSwitch.OFF)), resultado.ordenes());
    }

    @Test
    void casoLoEncendidoEntraJustoNoSeApagaPorRedondeo() {
        // 1,1 + 2,2 en double es 3,3000000000000003: no es pasarse de 3,3 kW
        HabitacionDTO habA = new HabitacionDTO("A", 21.0, 18.0, 1.1, EstadoSwitch.ON);
        HabitacionDTO habB = new HabitacionDTO("B", 21.0, 18.0, 2.2, EstadoSwitch.ON);

        Salida resultado = new EvaluadorCore().evaluarEstado(martes10hs, 3.3, tarifaHabiles, List.of(habA, habB));

        assertTrue(resultado.ordenes().isEmpty());
    }

    @Test
    void casoUnaHabitacionQueEntraJustoSeEnciendePeseAlRedondeo() {
        HabitacionDTO habA = new HabitacionDTO("A", 21.0, 18.0, 1.1, EstadoSwitch.ON);
        HabitacionDTO habB = new HabitacionDTO("B", 21.0, 18.0, 2.2, EstadoSwitch.OFF);

        Salida resultado = new EvaluadorCore().evaluarEstado(martes10hs, 3.3, tarifaHabiles, List.of(habA, habB));

        assertEquals(List.of(new Orden("B", AccionSwitch.ON)), resultado.ordenes());
    }

    @Test
    void casoLaQueSeApagaPorSobrarNoSeVuelveAEncenderEnElMismoCiclo() {
        HabitacionDTO habA = new HabitacionDTO("A", 21.0, 18.0, 2.0, EstadoSwitch.ON);
        HabitacionDTO habB = new HabitacionDTO("B", 21.0, 20.0, 2.0, EstadoSwitch.ON);
        HabitacionDTO habC = new HabitacionDTO("C", 21.0, 15.0, 2.0, EstadoSwitch.OFF);

        Salida resultado = new EvaluadorCore().evaluarEstado(
                martes10hs, 3.0, tarifaHabiles, List.of(habA, habB, habC));

        // Sobra B; C tiene más frío pero no entra junto a A
        assertEquals(List.of(new Orden("B", AccionSwitch.OFF)), resultado.ordenes());
    }

    // 10. TESTS DE PUNTA ANTICIPADA (si empieza antes de la próxima evaluación, se apaga ya)
    private final ZonedDateTime lunes165930 = ZonedDateTime.of(2026, 10, 5, 16, 59, 30, 0, ZoneId.of("UTC"));
    private final Duration unMinuto = Duration.ofMinutes(1);

    @Test
    void casoLaPuntaEmpiezaAntesDeLaProximaEvaluacionApagaYa() {
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 15.0, 2.0, EstadoSwitch.ON);

        Salida resultado = new EvaluadorCore().evaluarEstado(
                lunes165930, unMinuto, 10.0, tarifaHabiles, List.of(habA));

        assertEquals(List.of(new Orden("A", AccionSwitch.OFF)), resultado.ordenes());
    }

    @Test
    void casoLaPuntaEmpiezaAntesDeLaProximaEvaluacionNoEnciende() {
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 15.0, 2.0, EstadoSwitch.OFF);

        Salida resultado = new EvaluadorCore().evaluarEstado(
                lunes165930, unMinuto, 10.0, tarifaHabiles, List.of(habA));

        assertTrue(resultado.ordenes().isEmpty());
    }

    @Test
    void casoLaPuntaTodaviaNoLlegaALaProximaEvaluacionSigueNormal() {
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 15.0, 2.0, EstadoSwitch.OFF);

        Salida resultado = new EvaluadorCore().evaluarEstado(
                lunes165930.minusMinutes(2), unMinuto, 10.0, tarifaHabiles, List.of(habA));

        assertEquals(List.of(new Orden("A", AccionSwitch.ON)), resultado.ordenes());
    }

    @Test
    void casoSinAnticipacionSoloCuentaLaHoraActual() {
        HabitacionDTO habA = new HabitacionDTO("A", 22.0, 15.0, 2.0, EstadoSwitch.ON);

        Salida resultado = new EvaluadorCore().evaluarEstado(lunes165930, 10.0, tarifaHabiles, List.of(habA));

        assertTrue(resultado.ordenes().isEmpty());
    }

    // 11. TESTS DE APROVECHAMIENTO (con potencia escasa, la combinación que más usa la contratada)
    @Test
    void casoPotenciaEscasaEnciendeLaCombinacionQueMasAprovechaLaContratada() {
        // El sitio de F05 de ecowarm-run: empatadas, la de 1,2 kW sola dejaría 0,8 kW libres donde no entra nadie
        HabitacionDTO room1 = new HabitacionDTO("room1", 20.3, 19.5, 1.2, EstadoSwitch.OFF);
        HabitacionDTO room2 = new HabitacionDTO("room2", 21.8, 21.0, 0.9, EstadoSwitch.OFF);
        HabitacionDTO room3 = new HabitacionDTO("room3", 22.1, 21.3, 0.9, EstadoSwitch.OFF);
        HabitacionDTO room4 = new HabitacionDTO("room4", 20.4, 19.6, 1.0, EstadoSwitch.OFF);

        Salida resultado = new EvaluadorCore().evaluarEstado(
                martes10hs, 2.0, tarifaHabiles, List.of(room1, room2, room3, room4));

        // room2 y room4 suman 1,9 kW (room3 con room4, también; entre iguales va primero room2 en el inventario)
        assertEquals(List.of(new Orden("room2", AccionSwitch.ON), new Orden("room4", AccionSwitch.ON)),
                resultado.ordenes());
    }

    @Test
    void casoEntreCombinacionesQueAprovechanLoMismoEnciendeLasMasLejosDeSuObjetivo() {
        HabitacionDTO habA = new HabitacionDTO("A", 21.0, 20.0, 1.0, EstadoSwitch.OFF);
        HabitacionDTO habB = new HabitacionDTO("B", 21.0, 18.0, 1.0, EstadoSwitch.OFF);
        HabitacionDTO habC = new HabitacionDTO("C", 21.0, 19.0, 1.0, EstadoSwitch.OFF);

        Salida resultado = new EvaluadorCore().evaluarEstado(
                martes10hs, 2.0, tarifaHabiles, List.of(habA, habB, habC));

        assertEquals(List.of(new Orden("B", AccionSwitch.ON), new Orden("C", AccionSwitch.ON)), resultado.ordenes());
    }

    @Test
    void casoLaPotenciaQueQuedaLibreSeCompletaConLaCombinacionQueMejorEntra() {
        // A ya está encendida y deja 1,5 kW libres: B (1,0) y C (0,5) los llenan; D (1,2) sola no
        HabitacionDTO habA = new HabitacionDTO("A", 21.0, 18.0, 1.5, EstadoSwitch.ON);
        HabitacionDTO habB = new HabitacionDTO("B", 21.0, 19.0, 1.0, EstadoSwitch.OFF);
        HabitacionDTO habC = new HabitacionDTO("C", 21.0, 20.0, 0.5, EstadoSwitch.OFF);
        HabitacionDTO habD = new HabitacionDTO("D", 21.0, 17.0, 1.2, EstadoSwitch.OFF);

        Salida resultado = new EvaluadorCore().evaluarEstado(
                martes10hs, 3.0, tarifaHabiles, List.of(habA, habB, habC, habD));

        assertEquals(List.of(new Orden("B", AccionSwitch.ON), new Orden("C", AccionSwitch.ON)), resultado.ordenes());
    }

    @Test
    void casoBajaLaContratadaSiguenEncendidasLasQueMasLaAprovechan() {
        // 3,5 kW encendidos y ahora 2,5 contratados. Apagar A (la más cerca) dejaría 2,0; apagar B deja 2,5
        HabitacionDTO habA = new HabitacionDTO("A", 21.0, 20.5, 1.5, EstadoSwitch.ON);
        HabitacionDTO habB = new HabitacionDTO("B", 21.0, 19.0, 1.0, EstadoSwitch.ON);
        HabitacionDTO habC = new HabitacionDTO("C", 21.0, 18.0, 1.0, EstadoSwitch.ON);

        Salida resultado = new EvaluadorCore().evaluarEstado(
                martes10hs, 2.5, tarifaHabiles, List.of(habA, habB, habC));

        assertEquals(List.of(new Orden("B", AccionSwitch.OFF)), resultado.ordenes());
    }

    @Test
    void casoConMuchasHabitacionesConFrioLasQueNoSeCombinanSeSumanSiEntran() {
        // MAX_A_COMBINAR + 2 habitaciones de 1 kW con 17,5 kW libres: entran 17, una por una o combinadas
        List<HabitacionDTO> habitaciones = new ArrayList<>();
        for (int i = 0; i < EvaluadorCore.MAX_A_COMBINAR + 2; i++) {
            habitaciones.add(new HabitacionDTO("h" + i, 21.0, 15.0, 1.0, EstadoSwitch.OFF));
        }

        Salida resultado = new EvaluadorCore().evaluarEstado(martes10hs, 17.5, tarifaHabiles, habitaciones);

        assertEquals(17, resultado.ordenes().size());
        assertTrue(resultado.ordenes().stream().allMatch(o -> o.accion() == AccionSwitch.ON));
        assertEquals("h16", resultado.ordenes().get(16).idHabitacion());
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
