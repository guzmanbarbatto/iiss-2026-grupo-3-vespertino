package com.ioteste.engine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ioteste.core.domain.EstadoSwitch;
import com.ioteste.engine.RelojMutable;
import com.ioteste.engine.domain.EstadoControl;
import com.ioteste.engine.domain.Franja;
import com.ioteste.engine.domain.Habitacion;
import com.ioteste.engine.domain.InventarioDTO;
import com.ioteste.engine.domain.Sitio;
import com.ioteste.engine.domain.Tarifa;
import com.ioteste.engine.repository.HistoricoRepository;
import com.ioteste.engine.repository.HistoricoRepository.Lectura;
import com.ioteste.engine.repository.SitioRepository;
import com.ioteste.engine.service.SwitchClientService.Resultado;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.TaskScheduler;

/**
 * Ciclo de control del engine con el tiempo fijado: cada test sabe si está dentro o fuera de la tarifa
 * punta (HABILES de 17:00 a 23:00, hora de Montevideo).
 */
@ExtendWith(MockitoExtension.class)
class EvaluadorJobServiceTest {

    private static final String URL_SW = "http://simulador:9090";
    private static final Instant MARTES_10HS = local(2026, 10, 6, 10, 0);
    private static final Instant LUNES_18HS = local(2026, 10, 5, 18, 0);
    private static final Instant INSTANTE_REAL = Instant.parse("2026-11-03T15:00:00Z");

    @Mock private SitioRepository sitioRepository;
    @Mock private HistoricoRepository historicoRepository;
    @Mock private SwitchClientService switchClient;
    @Mock private TaskScheduler scheduler;
    @Mock private ScheduledFuture<?> tarea;

    private final RelojMutable clock = new RelojMutable(INSTANTE_REAL);
    private final Map<String, Lectura> lecturas = new HashMap<>();
    private EvaluadorJobService motor;

    @BeforeEach
    void setUp() {
        motor = new EvaluadorJobService(sitioRepository, historicoRepository, switchClient,
                new RelojControl(clock), scheduler);
        lenient().doReturn(tarea).when(scheduler).scheduleAtFixedRate(any(Runnable.class), any(Duration.class));
        lenient().when(historicoRepository.ultimasLecturas()).thenReturn(lecturas);
        lenient().when(switchClient.comandarSwitch(anyString(), anyString(), anyString()))
                .thenReturn(Resultado.APLICADO);
    }

    // ---------------------------------------------------------------- reglas fuera de punta

    @Test
    void fueraDePuntaApagaLaHabitacionQueSuperoLaTemperatura() {
        iniciarEn(MARTES_10HS);
        sitio(10.0, habitacion("1", 20.0, 2.0));
        lectura("1", 25.0);
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.ON);

        motor.ejecutarCicloDeControl();

        verify(switchClient).comandarSwitch(URL_SW, "sw-1", "OFF");
        verify(switchClient, never()).comandarSwitch(URL_SW, "sw-1", "ON");
    }

    @Test
    void fueraDePuntaEnciendeLaHabitacionConFrioSiHayPotencia() {
        iniciarEn(MARTES_10HS);
        sitio(10.0, habitacion("1", 22.0, 2.0));
        lectura("1", 15.0);
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.OFF);

        motor.ejecutarCicloDeControl();

        verify(switchClient).comandarSwitch(URL_SW, "sw-1", "ON");
    }

    @Test
    void sinPotenciaSuficienteNoEnciendeAunqueHagaFrio() {
        iniciarEn(MARTES_10HS);
        sitio(1.0, habitacion("1", 22.0, 2.0));
        lectura("1", 15.0);
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.OFF);

        motor.ejecutarCicloDeControl();

        verify(switchClient, never()).comandarSwitch(anyString(), anyString(), anyString());
    }

    // ---------------------------------------------------------------- tarifa punta y reloj lógico

    @Test
    void enTarifaPuntaApagaLoEncendidoYNoEnciendeNada() {
        iniciarEn(LUNES_18HS);
        sitio(10.0, habitacion("1", 22.0, 2.0), habitacion("2", 22.0, 2.0));
        lectura("1", 15.0);
        lectura("2", 15.0);
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.ON);
        when(switchClient.consultarEstado(URL_SW, "sw-2")).thenReturn(EstadoSwitch.OFF);

        motor.ejecutarCicloDeControl();

        verify(switchClient).comandarSwitch(URL_SW, "sw-1", "OFF");
        verify(switchClient, never()).comandarSwitch(anyString(), anyString(), eq("ON"));
    }

    @Test
    void elRelojAceleradoEntraEnLaPuntaAunqueElTiempoRealCasiNoAvance() {
        Instant lunes1659 = local(2026, 10, 5, 16, 59);
        motor.iniciarMotor(lunes1659, 60.0);
        sitio(10.0, habitacion("1", 22.0, 2.0));
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.ON);

        clock.avanzar(Duration.ofSeconds(2)); // 2 s reales x 60 = 16:59 + 2 min = 17:01 lógico
        lectura("1", 15.0);
        motor.ejecutarCicloDeControl();

        verify(switchClient).comandarSwitch(URL_SW, "sw-1", "OFF");
    }

    @Test
    void siLaPuntaEmpiezaAntesDelProximoCicloApagaYaParaNoEntrarEncendido() {
        // A ×1 el ciclo corre cada 30 s: a las 16:59:30 del lunes la punta empieza antes del próximo
        iniciarEn(local(2026, 10, 5, 16, 59).plusSeconds(30));
        sitio(10.0, habitacion("1", 22.0, 2.0));
        lectura("1", 15.0);
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.ON);

        motor.ejecutarCicloDeControl();

        verify(switchClient).comandarSwitch(URL_SW, "sw-1", "OFF");
    }

    @Test
    void unosMinutosAntesDeLaPuntaSigueControlandoNormalmente() {
        iniciarEn(local(2026, 10, 5, 16, 57));
        sitio(10.0, habitacion("1", 22.0, 2.0));
        lectura("1", 15.0);
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.OFF);

        motor.ejecutarCicloDeControl();

        verify(switchClient).comandarSwitch(URL_SW, "sw-1", "ON");
    }

    @Test
    void diasDeTarifaFueraDelContratoNoAplicanPunta() {
        iniciarEn(LUNES_18HS);
        when(sitioRepository.obtenerInventario()).thenReturn(Optional.of(new InventarioDTO("2.0",
                new Sitio("s", "Casa", 10.0, new Tarifa(new Franja("17:00", "23:00", "MONDAY,TUESDAY"))),
                List.of(habitacion("1", 22.0, 2.0)))));
        lectura("1", 15.0);
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.ON);

        motor.ejecutarCicloDeControl();

        verify(switchClient, never()).comandarSwitch(anyString(), anyString(), anyString());
    }

    // ---------------------------------------------------------------- fail-safe de termostatos

    @Test
    void aVelocidadRealUnTermostatoCalladoMasDeDiezMinutosSeDaPorCaido() {
        iniciarEn(MARTES_10HS);
        sitio(10.0, habitacion("1", 22.0, 2.0));
        lecturaRecibidaHace("1", 15.0, Duration.ofMinutes(11));
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.ON);

        motor.ejecutarCicloDeControl();

        verify(switchClient).comandarSwitch(URL_SW, "sw-1", "OFF");
    }

    @Test
    void aVelocidadRealUnaLecturaDeHaceNueveMinutosSigueVigente() {
        iniciarEn(MARTES_10HS);
        sitio(10.0, habitacion("1", 22.0, 2.0));
        lecturaRecibidaHace("1", 15.0, Duration.ofMinutes(9));
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.OFF);

        motor.ejecutarCicloDeControl();

        verify(switchClient).comandarSwitch(URL_SW, "sw-1", "ON");
    }

    @Test
    void conAceleracionAltaLasLecturasNoSeVencenAntesDeTiempo() {
        // x600: 600 s simulados duran 1 s real. El termostato publica cada 5 s, así que antes cada lectura
        // llegaba "vencida" y el engine apagaba todo. Ahora se espera a que pierda 3 lecturas (15 s).
        motor.iniciarMotor(MARTES_10HS, 600.0);
        sitio(10.0, habitacion("1", 22.0, 2.0));
        lecturaRecibidaHace("1", 15.0, Duration.ofSeconds(10), Duration.ofSeconds(5));
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.OFF);

        motor.ejecutarCicloDeControl();

        verify(switchClient).comandarSwitch(URL_SW, "sw-1", "ON");
    }

    @Test
    void conAceleracionAltaUnTermostatoQuePierdeTresLecturasSeDaPorCaido() {
        motor.iniciarMotor(MARTES_10HS, 600.0);
        sitio(10.0, habitacion("1", 22.0, 2.0));
        lecturaRecibidaHace("1", 15.0, Duration.ofSeconds(16), Duration.ofSeconds(5));
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.ON);

        motor.ejecutarCicloDeControl();

        verify(switchClient).comandarSwitch(URL_SW, "sw-1", "OFF");
    }

    @Test
    void unTermostatoQuePublicaMasLentoNoSeDaPorCaidoAntesDeTiempo() {
        // Publica cada 30 s reales: con un límite fijo de 15 s, cada lectura vencía antes de la siguiente
        motor.iniciarMotor(MARTES_10HS, 600.0);
        sitio(10.0, habitacion("1", 22.0, 2.0));
        lecturaRecibidaHace("1", 15.0, Duration.ofSeconds(45), Duration.ofSeconds(30));
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.OFF);

        motor.ejecutarCicloDeControl();

        verify(switchClient).comandarSwitch(URL_SW, "sw-1", "ON");
    }

    @Test
    void elLimiteDeSilencioEsDiezMinutosSimuladosOTresLecturasPerdidasLoQueSeaMayor() {
        Duration cadaCinco = Duration.ofSeconds(5);
        assertThat(motor.silencioMaximo(cadaCinco)).isEqualTo(Duration.ofMinutes(10)); // detenido: tiempo real
        motor.iniciarMotor(MARTES_10HS, 10.0);
        assertThat(motor.silencioMaximo(cadaCinco)).isEqualTo(Duration.ofSeconds(60));
        motor.iniciarMotor(MARTES_10HS, 60.0);
        assertThat(motor.silencioMaximo(cadaCinco)).isEqualTo(Duration.ofSeconds(15)); // 10 s < 3 lecturas
        assertThat(motor.silencioMaximo(Duration.ofSeconds(30))).isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void sinRitmoConocidoSeSuponeUnoProvisorioYHayUnPisoAbsoluto() {
        motor.iniciarMotor(MARTES_10HS, 600.0);
        assertThat(motor.silencioMaximo(null)).isEqualTo(Duration.ofSeconds(30)); // una sola lectura: 3 × 10 s
        assertThat(motor.silencioMaximo(Duration.ofMillis(100))).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void sinLecturasApagaLoEncendidoYNoEnciendeLoApagado() {
        iniciarEn(MARTES_10HS);
        sitio(10.0, habitacion("1", 22.0, 2.0), habitacion("2", 22.0, 2.0));
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.ON);
        when(switchClient.consultarEstado(URL_SW, "sw-2")).thenReturn(EstadoSwitch.OFF);

        motor.ejecutarCicloDeControl();

        verify(switchClient).comandarSwitch(URL_SW, "sw-1", "OFF");
        verify(switchClient, never()).comandarSwitch(anyString(), eq("sw-2"), anyString());
    }

    @Test
    void laVigenciaSeMideDesdeQueLlegoLaLecturaYNoPorSuTimestamp() {
        // El ts puede venir en el reloj virtual del simulador (acá, días atrás): lo que cuenta es la llegada
        iniciarEn(MARTES_10HS);
        sitio(10.0, habitacion("1", 22.0, 2.0));
        lecturas.put("hab-1", new Lectura(Instant.parse("2020-01-01T00:00:00Z"), clock.instant(), 15.0));
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.OFF);

        motor.ejecutarCicloDeControl();

        verify(switchClient).comandarSwitch(URL_SW, "sw-1", "ON");
    }

    // ---------------------------------------------------------------- estado real de los switches

    @Test
    void elEstadoDelSwitchSePideEnCadaCicloYSeCorrigeUnCambioExterno() {
        iniciarEn(MARTES_10HS);
        sitio(10.0, habitacion("1", 20.0, 2.0));
        lectura("1", 25.0);
        // Ciclo 1: apagado y caliente, nada que hacer. Entre ciclos alguien (p. ej. el MCP) lo enciende.
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.OFF, EstadoSwitch.ON);

        motor.ejecutarCicloDeControl();
        verify(switchClient, never()).comandarSwitch(anyString(), anyString(), anyString());

        motor.ejecutarCicloDeControl();
        verify(switchClient).comandarSwitch(URL_SW, "sw-1", "OFF");
        verify(switchClient, times(2)).consultarEstado(URL_SW, "sw-1");
    }

    @Test
    void unSwitchQueNoSePudoConsultarSeComandaYSeReconsultaEnElCicloSiguiente() {
        iniciarEn(MARTES_10HS);
        sitio(10.0, habitacion("1", 22.0, 2.0));
        lectura("1", 15.0);
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.DESCONOCIDO, EstadoSwitch.OFF);

        motor.ejecutarCicloDeControl();
        motor.ejecutarCicloDeControl();

        verify(switchClient, times(2)).comandarSwitch(URL_SW, "sw-1", "ON");
    }

    @Test
    void unSwitchQueFallaAlComandarNoImpideComandarLosDemas() {
        iniciarEn(MARTES_10HS);
        sitio(10.0, habitacion("1", 20.0, 2.0), habitacion("2", 20.0, 2.0));
        lectura("1", 25.0);
        lectura("2", 25.0);
        when(switchClient.consultarEstado(anyString(), anyString())).thenReturn(EstadoSwitch.ON);
        when(switchClient.comandarSwitch(URL_SW, "sw-1", "OFF")).thenReturn(Resultado.FALLIDO);

        motor.ejecutarCicloDeControl();

        verify(switchClient).comandarSwitch(URL_SW, "sw-2", "OFF");
    }

    // ---------------------------------------------------------------- switches a la vez

    @Test
    void losSwitchesSeConsultanALaVezYUnoQueNoContestaNoDemoraALosDemas() {
        iniciarEn(MARTES_10HS);
        sitio(10.0, habitacion("1", 20.0, 2.0), habitacion("2", 20.0, 2.0), habitacion("3", 20.0, 2.0));
        lectura("1", 25.0);
        lectura("2", 25.0);
        lectura("3", 25.0);
        // Cada consulta espera a que lleguen las tres: de a una, la primera esperaría hasta vencer
        CountDownLatch llegaron = new CountDownLatch(3);
        List<Boolean> juntas = new CopyOnWriteArrayList<>();
        when(switchClient.consultarEstado(eq(URL_SW), anyString())).thenAnswer(pedido -> {
            llegaron.countDown();
            juntas.add(llegaron.await(2, TimeUnit.SECONDS));
            return EstadoSwitch.OFF;
        });

        motor.ejecutarCicloDeControl();

        assertThat(juntas).containsExactly(true, true, true);
    }

    @Test
    void losApagadosSalenALaVezYLosEncendidosRecienCuandoTerminan() {
        iniciarEn(MARTES_10HS);
        sitio(4.0, habitacion("1", 20.0, 2.0), habitacion("2", 20.0, 2.0),
                habitacion("3", 22.0, 2.0), habitacion("4", 22.0, 2.0));
        lectura("1", 25.0);
        lectura("2", 25.0);
        lectura("3", 15.0);
        lectura("4", 15.0);
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.ON);
        when(switchClient.consultarEstado(URL_SW, "sw-2")).thenReturn(EstadoSwitch.ON);
        when(switchClient.consultarEstado(URL_SW, "sw-3")).thenReturn(EstadoSwitch.OFF);
        when(switchClient.consultarEstado(URL_SW, "sw-4")).thenReturn(EstadoSwitch.OFF);
        CountDownLatch apagando = new CountDownLatch(2);
        List<String> eventos = new CopyOnWriteArrayList<>();
        when(switchClient.comandarSwitch(eq(URL_SW), anyString(), eq("OFF"))).thenAnswer(pedido -> {
            apagando.countDown();
            boolean aLaVez = apagando.await(2, TimeUnit.SECONDS);
            eventos.add("OFF " + pedido.getArgument(1) + (aLaVez ? " a la vez" : " de a uno"));
            return Resultado.APLICADO;
        });
        when(switchClient.comandarSwitch(eq(URL_SW), anyString(), eq("ON"))).thenAnswer(pedido -> {
            eventos.add("ON " + pedido.getArgument(1));
            return Resultado.APLICADO;
        });

        motor.ejecutarCicloDeControl();

        assertThat(eventos).hasSize(4);
        assertThat(eventos.subList(0, 2)).containsExactlyInAnyOrder("OFF sw-1 a la vez", "OFF sw-2 a la vez");
        assertThat(eventos.subList(2, 4)).containsExactlyInAnyOrder("ON sw-3", "ON sw-4");
    }

    // ---------------------------------------------------------------- apagados que no se confirman

    @Test
    void siUnApagadoNoSeConfirmaNoSeEnciendeOtraQueNoEntraria() {
        // Hay que rotar: apagar room1 (llegó a su objetivo) para encender room2. Si el OFF no llega, room1 puede
        // seguir encendida y encender room2 pasaría la contratada
        iniciarEn(MARTES_10HS);
        sitio(2.0, habitacion("1", 20.0, 2.0), habitacion("2", 22.0, 2.0));
        lectura("1", 25.0);
        lectura("2", 15.0);
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.ON);
        when(switchClient.consultarEstado(URL_SW, "sw-2")).thenReturn(EstadoSwitch.OFF);
        when(switchClient.comandarSwitch(URL_SW, "sw-1", "OFF")).thenReturn(Resultado.FALLIDO);

        motor.ejecutarCicloDeControl();

        verify(switchClient).comandarSwitch(URL_SW, "sw-1", "OFF");
        verify(switchClient, never()).comandarSwitch(URL_SW, "sw-2", "ON");
    }

    @Test
    void cuandoElApagadoSeConfirmaSeEnciendeLaQueEsperaba() {
        iniciarEn(MARTES_10HS);
        sitio(2.0, habitacion("1", 20.0, 2.0), habitacion("2", 22.0, 2.0));
        lectura("1", 25.0);
        lectura("2", 15.0);
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.ON);
        when(switchClient.consultarEstado(URL_SW, "sw-2")).thenReturn(EstadoSwitch.OFF);
        when(switchClient.comandarSwitch(URL_SW, "sw-1", "OFF")).thenReturn(Resultado.FALLIDO, Resultado.APLICADO);

        motor.ejecutarCicloDeControl();
        motor.ejecutarCicloDeControl();

        verify(switchClient, times(2)).comandarSwitch(URL_SW, "sw-1", "OFF");
        verify(switchClient, times(1)).comandarSwitch(URL_SW, "sw-2", "ON");
    }

    @Test
    void unEncendidoQueEntraAunqueElApagadoFalleSeEnviaIgual() {
        iniciarEn(MARTES_10HS);
        sitio(4.0, habitacion("1", 20.0, 2.0), habitacion("2", 22.0, 2.0));
        lectura("1", 25.0);
        lectura("2", 15.0);
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.ON);
        when(switchClient.consultarEstado(URL_SW, "sw-2")).thenReturn(EstadoSwitch.OFF);
        when(switchClient.comandarSwitch(URL_SW, "sw-1", "OFF")).thenReturn(Resultado.FALLIDO);

        motor.ejecutarCicloDeControl();

        // room1 se cuenta encendida (2 kW) y room2 igual entra en los 4 kW
        verify(switchClient).comandarSwitch(URL_SW, "sw-2", "ON");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void elEncendidoPostergadoSeAvisaUnaVezYOtraAlRecuperarse(CapturedOutput salida) {
        iniciarEn(MARTES_10HS);
        sitio(2.0, habitacion("1", 20.0, 2.0), habitacion("2", 22.0, 2.0));
        lectura("1", 25.0);
        lectura("2", 15.0);
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.ON);
        when(switchClient.consultarEstado(URL_SW, "sw-2")).thenReturn(EstadoSwitch.OFF);
        when(switchClient.comandarSwitch(URL_SW, "sw-1", "OFF"))
                .thenReturn(Resultado.FALLIDO, Resultado.FALLIDO, Resultado.FALLIDO, Resultado.APLICADO);

        for (int i = 0; i < 4; i++) {
            motor.ejecutarCicloDeControl();
        }

        assertThat(salida.getOut().split("No se enciende hab-2", -1)).hasSize(2);
        assertThat(salida.getOut().split("Los apagados se vuelven a confirmar", -1)).hasSize(2);
    }

    @Test
    void cadaSwitchSeConsultaYSeComandaEnElUrlSwitchDeSuHabitacion() {
        // §3 y §4: los switches de un sitio pueden estar en servidores distintos
        iniciarEn(MARTES_10HS);
        Habitacion enOtroServidor = new Habitacion("hab-2", "Cuarto 2", 22.0, 1.0, "ht-2",
                "ht-2/status/temperature:0", "sw-2", "http://switches-b:8081");
        sitio(10.0, habitacion("1", 22.0, 1.0), enOtroServidor);
        lectura("1", 15.0);
        lectura("2", 15.0);
        when(switchClient.consultarEstado(anyString(), anyString())).thenReturn(EstadoSwitch.OFF);

        motor.ejecutarCicloDeControl();

        verify(switchClient).consultarEstado("http://switches-b:8081", "sw-2");
        verify(switchClient).comandarSwitch(URL_SW, "sw-1", "ON");
        verify(switchClient).comandarSwitch("http://switches-b:8081", "sw-2", "ON");
    }

    @Test
    void alIniciarElControlSeReleeElEstadoDeTodosLosSwitches() {
        motor.iniciarMotor(MARTES_10HS, 1.0);

        verify(switchClient).olvidarEstados();
    }

    // ---------------------------------------------------------------- robustez del ciclo

    @Test
    void sinSitioCargadoNoComandaNada() {
        iniciarEn(MARTES_10HS);
        when(sitioRepository.obtenerInventario()).thenReturn(Optional.empty());

        motor.ejecutarCicloDeControl();

        sinTraficoConLosSwitches();
    }

    // ---------------------------------------------------------------- logs legibles

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void cadaCicloConCambiosDejaUnaLineaConHoraSimuladaYPotencia(CapturedOutput salida) {
        iniciarEn(MARTES_10HS);
        sitio(3.0, habitacion("1", 22.0, 2.0), habitacion("2", 20.0, 0.8));
        lectura("1", 15.0);
        lectura("2", 25.0);
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.OFF);
        when(switchClient.consultarEstado(URL_SW, "sw-2")).thenReturn(EstadoSwitch.ON);

        motor.ejecutarCicloDeControl();

        assertThat(salida.getOut())
                .contains("[simulado mar 06/10 10:00] hab-2 → OFF, hab-1 → ON | en uso 2.0 de 3.0 kW: hab-1");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void elFailSafeAvisaCuandoCambiaYNoEnCadaCiclo(CapturedOutput salida) {
        iniciarEn(MARTES_10HS);
        sitio(10.0, habitacion("1", 22.0, 2.0));
        when(switchClient.consultarEstado(URL_SW, "sw-1")).thenReturn(EstadoSwitch.OFF);

        motor.ejecutarCicloDeControl();
        motor.ejecutarCicloDeControl();
        lectura("1", 21.9);
        motor.ejecutarCicloDeControl();

        assertThat(salida.getOut().split("Fail-safe: sin lecturas recientes de hab-1", -1)).hasSize(2);
        assertThat(salida.getOut()).contains("Volvieron las lecturas de todas las habitaciones");
    }

    @Test
    void laHoraSimuladaSeMuestraEnHoraLocalConElDia() {
        assertThat(EvaluadorJobService.horaSimulada(ZonedDateTime.parse("2026-10-05T20:00:00Z")))
                .isEqualTo("lun 05/10 17:00");
        assertThat(EvaluadorJobService.horaSimulada(ZonedDateTime.parse("2026-10-11T12:30:00Z")))
                .isEqualTo("dom 11/10 09:30");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void sinSitioAvisaUnaSolaVezYNoEnCadaCiclo(CapturedOutput salida) {
        iniciarEn(MARTES_10HS);
        when(sitioRepository.obtenerInventario()).thenReturn(Optional.empty());

        motor.ejecutarCicloDeControl();
        motor.ejecutarCicloDeControl();
        motor.ejecutarCicloDeControl();

        assertThat(salida.getOut().split("No hay un sitio cargado", -1)).hasSize(2);
    }

    @Test
    void unaFallaDeBaseDeDatosNoCortaElCiclo() {
        iniciarEn(MARTES_10HS);
        when(sitioRepository.obtenerInventario()).thenThrow(new DataAccessResourceFailureException("caída"));

        motor.ejecutarCicloDeControl();

        sinTraficoConLosSwitches();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void unaFallaQueDuraVariosCiclosSeAvisaAlEmpezarYAlTerminar(CapturedOutput salida) {
        iniciarEn(MARTES_10HS);
        when(sitioRepository.obtenerInventario())
                .thenThrow(new DataAccessResourceFailureException("base caída"))
                .thenThrow(new DataAccessResourceFailureException("base caída"))
                .thenThrow(new DataAccessResourceFailureException("base caída"))
                .thenReturn(Optional.empty());

        for (int i = 0; i < 4; i++) {
            motor.ejecutarCicloDeControl();
        }

        assertThat(salida.getOut().split("Error en el ciclo de control", -1)).hasSize(2);
        assertThat(salida.getOut().split("El ciclo de control vuelve a funcionar", -1)).hasSize(2);
    }

    // ---------------------------------------------------------------- iniciar y detener

    @Test
    void iniciarProgramaUnCicloCada30SegundosLogicos() {
        EstadoControl estado = motor.iniciarMotor(MARTES_10HS, 1.0);

        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofSeconds(30)));
        assertThat(estado.estado()).isEqualTo("EN_EJECUCION");
        assertThat(estado.factorTiempo()).isEqualTo(1.0);
    }

    @Test
    void conAceleracionAltaElIntervaloRealNoBajaDelMinimo() {
        motor.iniciarMotor(MARTES_10HS, 1000.0);

        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofMillis(500)));
    }

    @Test
    void laTareaProgramadaEjecutaElCicloDeControl() {
        motor.iniciarMotor(MARTES_10HS, 1.0);
        ArgumentCaptor<Runnable> ciclo = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(ciclo.capture(), any(Duration.class));
        when(sitioRepository.obtenerInventario()).thenReturn(Optional.empty());

        ciclo.getValue().run();

        verify(sitioRepository).obtenerInventario();
    }

    @Test
    void reiniciarCancelaLaTareaAnteriorYResincroniza() {
        motor.iniciarMotor(MARTES_10HS, 1.0);
        EstadoControl estado = motor.iniciarMotor(LUNES_18HS, 10.0);

        verify(tarea).cancel(false);
        assertThat(estado.fechaHora()).isEqualTo("2026-10-05T21:00:00.000Z");
        assertThat(estado.factorTiempo()).isEqualTo(10.0);
    }

    @Test
    void detenerCancelaLaTareaYDejaElEstadoEnNull() {
        motor.iniciarMotor(MARTES_10HS, 1.0);

        EstadoControl estado = motor.detenerMotor();

        verify(tarea).cancel(false);
        assertThat(estado).isEqualTo(new EstadoControl("DETENIDO", null, null, null));
        assertThat(motor.estadoControl()).isEqualTo(estado);
    }

    @Test
    void detenerUnControlDetenidoEsIdempotente() {
        assertThat(motor.detenerMotor().estado()).isEqualTo("DETENIDO");
        verifyNoInteractions(scheduler);
    }

    // ---------------------------------------------------------------- auxiliares

    private static Instant local(int anio, int mes, int dia, int hora, int minuto) {
        return ZonedDateTime.of(anio, mes, dia, hora, minuto, 0, 0, RelojControl.ZONA_LOCAL).toInstant();
    }

    private void iniciarEn(Instant fechaHora) {
        motor.iniciarMotor(fechaHora, 1.0);
    }

    private void sitio(double potenciaContratada, Habitacion... habitaciones) {
        when(sitioRepository.obtenerInventario()).thenReturn(Optional.of(new InventarioDTO("2.0",
                new Sitio("casa", "Casa", potenciaContratada, new Tarifa(new Franja("17:00", "23:00", "HABILES"))),
                List.of(habitaciones))));
    }

    /** El ciclo no consultó ni comandó ningún switch. */
    private void sinTraficoConLosSwitches() {
        verify(switchClient, never()).consultarEstado(anyString(), anyString());
        verify(switchClient, never()).comandarSwitch(anyString(), anyString(), anyString());
    }

    private static Habitacion habitacion(String n, double esperada, double potenciaKw) {
        return new Habitacion("hab-" + n, "Cuarto " + n, esperada, potenciaKw, "ht-" + n,
                "ht-" + n + "/status/temperature:0", "sw-" + n, URL_SW);
    }

    /** Una lectura que acaba de llegar. */
    private void lectura(String n, double temperatura) {
        lecturaRecibidaHace(n, temperatura, Duration.ZERO);
    }

    private void lecturaRecibidaHace(String n, double temperatura, Duration hace) {
        lecturaRecibidaHace(n, temperatura, hace, null);
    }

    /** Una lectura de un termostato que publica cada {@code ritmo} ({@code null}: todavía no se conoce). */
    private void lecturaRecibidaHace(String n, double temperatura, Duration hace, Duration ritmo) {
        Instant recibida = clock.instant().minus(hace);
        lecturas.put("hab-" + n, new Lectura(recibida, recibida, temperatura, ritmo));
    }
}
