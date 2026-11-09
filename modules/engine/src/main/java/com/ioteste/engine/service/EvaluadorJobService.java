package com.ioteste.engine.service;

import com.ioteste.core.domain.AccionSwitch;
import com.ioteste.core.domain.EstadoSwitch;
import com.ioteste.core.domain.HabitacionDTO;
import com.ioteste.core.domain.Orden;
import com.ioteste.core.domain.Salida;
import com.ioteste.core.domain.TarifaPunta;
import com.ioteste.core.service.EvaluadorCore;
import com.ioteste.engine.domain.EstadoControl;
import com.ioteste.engine.domain.Franja;
import com.ioteste.engine.domain.Habitacion;
import com.ioteste.engine.domain.InventarioDTO;
import com.ioteste.engine.repository.HistoricoRepository;
import com.ioteste.engine.repository.HistoricoRepository.Lectura;
import com.ioteste.engine.repository.SitioRepository;
import com.ioteste.engine.service.SwitchClientService.Resultado;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

/** Motor del control: cada 30 s de tiempo lógico arma el estado de la casa, consulta al core y comanda. */
@Service
public class EvaluadorJobService {

    private static final Logger LOG = LoggerFactory.getLogger(EvaluadorJobService.class);

    static final long INTERVALO_LOGICO_MS = 30_000;
    static final long INTERVALO_MINIMO_REAL_MS = 500;
    /** Sin lecturas durante este tiempo simulado, el termostato se considera caído. */
    static final Duration LECTURA_VENCIDA = Duration.ofSeconds(600);
    /**
     * Un termostato nunca se da por caído antes de perder esta cantidad de lecturas seguidas. Con aceleración
     * alta, 600 s simulados pueden durar menos que lo que el termostato tarda en volver a publicar (el de la
     * cátedra publica cada 5 s reales; con ×600, 600 s simulados son 1 s real) y cada lectura llegaría
     * "vencida". El ritmo de cada termostato se mide de sus últimas lecturas: no se supone ninguno.
     */
    static final int LECTURAS_PERDIDAS = 3;
    /** Ritmo que se supone mientras un termostato tiene una sola lectura y todavía no se puede medir. */
    static final Duration RITMO_SUPUESTO = Duration.ofSeconds(10);
    /** Piso absoluto: un termostato que publica muy seguido no entra al fail-safe por una demora mínima. */
    static final Duration SILENCIO_MINIMO_REAL = Duration.ofSeconds(2);
    /** Temperatura que se asume sin lecturas válidas: fuerza el apagado (fail-safe). */
    static final double TEMPERATURA_FAIL_SAFE = 99.0;
    /**
     * Ciclos que una habitación queda fuera del reparto cuando su switch rechazó el {@code ON} y sigue apagado. Si
     * no, el core la volvía a elegir en cada ciclo y le guardaba una potencia que nunca usaba, mientras otras
     * habitaciones que sí entraban se quedaban sin calor.
     */
    static final int CICLOS_SIN_REINTENTAR_ON = 4;
    private static final DateTimeFormatter DIA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final String[] DIAS = {"lun", "mar", "mié", "jue", "vie", "sáb", "dom"};

    private final SitioRepository sitioRepository;
    private final HistoricoRepository historicoRepository;
    private final SwitchClientService switchClient;
    private final RelojControl reloj;
    private final TaskScheduler scheduler;
    private final EvaluadorCore evaluadorCore = new EvaluadorCore();

    private static final String FALLA_CICLO = "ciclo";
    private static final String FALLA_SIN_SITIO = "sin-sitio";
    private static final String FALLA_ON_POSTERGADO = "on-postergado";
    private static final String FALLA_ON_RECHAZADO = "on-rechazado:";
    /** Tolerancia de redondeo al comparar potencias (la misma idea que en el core). */
    private static final double TOLERANCIA_KW = 1e-9;

    private ScheduledFuture<?> tareaProgramada;
    /**
     * Si el control está en ejecución. Detener cancela los ciclos siguientes, pero no el que ya está corriendo: ese
     * ciclo lo mira antes de cada tanda de comandos y antes de cada comando, y no comanda nada más (§5.2). Antes, un
     * ciclo que esperaba a un switch lento mandaba su {@code ON} con el control ya detenido.
     */
    private volatile boolean enEjecucion;
    private final FallasActivas fallas = new FallasActivas();
    /** Habitaciones cuyo switch rechazó el {@code ON}, con los ciclos que les faltan para volver al reparto. */
    private final Map<String, Integer> onRechazado = new ConcurrentHashMap<>();
    private volatile List<String> ultimoSinLecturas = List.of();
    /** Hasta dónde se mira la tarifa punta: si empieza antes, se apaga ya (ver {@link #iniciarMotor}). */
    private volatile Duration anticipacionPunta = Duration.ZERO;

    public EvaluadorJobService(
            SitioRepository sitioRepository,
            HistoricoRepository historicoRepository,
            SwitchClientService switchClient,
            RelojControl reloj,
            TaskScheduler scheduler) {
        this.sitioRepository = sitioRepository;
        this.historicoRepository = historicoRepository;
        this.switchClient = switchClient;
        this.reloj = reloj;
        this.scheduler = scheduler;
    }

    /** Arranca el control (o lo re-sincroniza si ya corría) con el reloj lógico en {@code fechaHora}. */
    public synchronized EstadoControl iniciarMotor(Instant fechaHora, double factorTiempo) {
        cancelarTarea();
        reloj.sincronizar(fechaHora, factorTiempo);
        switchClient.olvidarEstados(); // al arrancar no se confía en lo que se sabía: se relee cada switch
        onRechazado.clear(); // y todas las habitaciones vuelven al reparto
        long intervaloReal = Math.max(INTERVALO_MINIMO_REAL_MS, (long) (INTERVALO_LOGICO_MS / factorTiempo));
        // Dos ciclos simulados: el próximo y un margen por la demora del ciclo y de los comandos. Así ningún
        // calefactor queda encendido dentro de la punta esperando a que el ciclo siguiente la detecte
        anticipacionPunta = Duration.ofMillis((long) (intervaloReal * factorTiempo)).multipliedBy(2);
        enEjecucion = true;
        tareaProgramada = scheduler.scheduleAtFixedRate(this::ejecutarCicloDeControl, Duration.ofMillis(intervaloReal));
        LOG.info("Control iniciado: hora simulada {} (×{}, un ciclo cada {} ms)",
                horaSimulada(fechaHora.atZone(RelojControl.ZONA_LOCAL)),
                BigDecimal.valueOf(factorTiempo).stripTrailingZeros().toPlainString(), intervaloReal);
        return reloj.estado();
    }

    /** Detiene el control. Es idempotente y no apaga lo que haya quedado encendido. */
    public synchronized EstadoControl detenerMotor() {
        enEjecucion = false; // primero: un ciclo en curso deja de comandar ya
        cancelarTarea();
        reloj.detener();
        LOG.info("Control detenido");
        return reloj.estado();
    }

    public EstadoControl estadoControl() {
        return reloj.estado();
    }

    /** Un ciclo de control. Nunca lanza: una excepción cancelaría las ejecuciones siguientes del scheduler. */
    public void ejecutarCicloDeControl() {
        try {
            ciclo();
            if (fallas.termino(FALLA_CICLO)) {
                LOG.info("El ciclo de control vuelve a funcionar");
            }
        } catch (RuntimeException e) {
            if (fallas.empezo(FALLA_CICLO)) {
                LOG.error("Error en el ciclo de control (se reintenta en cada ciclo; "
                        + "se avisará cuando se recupere): {}", e.getMessage(), e);
            } else {
                LOG.debug("El ciclo de control sigue fallando: {}", e.getMessage());
            }
        }
    }

    private void ciclo() {
        Optional<InventarioDTO> inventario = sitioRepository.obtenerInventario();
        ZonedDateTime ahora = reloj.ahora();
        if (inventario.isEmpty()) {
            if (fallas.empezo(FALLA_SIN_SITIO)) {
                LOG.warn("No hay un sitio cargado: el control espera el PUT /sitio del simulador");
            }
            return;
        }
        fallas.termino(FALLA_SIN_SITIO);
        evaluar(inventario.get(), ahora);
    }

    private void evaluar(InventarioDTO inventario, ZonedDateTime ahora) {
        Map<String, Lectura> lecturas = historicoRepository.ultimasLecturas();
        Map<String, Habitacion> porId = new HashMap<>();
        List<HabitacionDTO> habitaciones = new ArrayList<>();
        List<String> sinLecturasVigentes = new ArrayList<>();
        // Todos los switches a la vez: uno que no contesta no demora la consulta de los demás
        List<EstadoSwitch> estados = enParalelo(inventario.habitaciones(),
                hab -> switchClient.consultarEstado(hab.urlSwitch(), hab.idSwitch()));

        for (int i = 0; i < inventario.habitaciones().size(); i++) {
            Habitacion hab = inventario.habitaciones().get(i);
            porId.put(hab.id(), hab);
            Double temperatura = temperaturaVigente(lecturas.get(hab.id()));
            if (temperatura == null) {
                sinLecturasVigentes.add(hab.id());
                temperatura = TEMPERATURA_FAIL_SAFE;
            }
            habitaciones.add(new HabitacionDTO(hab.id(), hab.temperaturaEsperada(), temperatura, hab.potenciaKW(),
                    estados.get(i)));
        }

        if (!enEjecucion) {
            LOG.debug("El control se detuvo mientras se consultaban los switches: el ciclo no comanda");
            return;
        }
        avisarSinLecturas(sinLecturasVigentes, ahora);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Ciclo {} | potencia contratada {} kW | {}", ahora,
                    inventario.sitio().potenciaContratadaKW(), habitaciones);
        }

        Salida salida = evaluadorCore.evaluarEstado(ahora, anticipacionPunta,
                inventario.sitio().potenciaContratadaKW(), tarifaPunta(inventario.sitio().tarifa().punta()),
                sinLasQueRechazaronElOn(habitaciones));

        double contratada = inventario.sitio().potenciaContratadaKW();
        Map<String, EstadoSwitch> estadoFinal = new LinkedHashMap<>();
        habitaciones.forEach(h -> estadoFinal.put(h.id(), h.estadoActual()));
        List<String> cambios = new ArrayList<>();
        // Primero todos los apagados, a la vez, y recién cuando terminan los encendidos: así la potencia se libera
        // antes de volver a ocuparla, y un switch que no contesta no demora a los demás
        comandar(ordenes(salida, AccionSwitch.OFF), porId, estadoFinal, cambios);
        if (!enEjecucion) {
            LOG.debug("El control se detuvo mientras salían los apagados: no se enciende nada");
            resumirSiHuboCambios(ahora, cambios, estadoFinal, habitaciones, contratada);
            return;
        }

        List<Orden> encendidos = new ArrayList<>();
        List<String> postergadas = new ArrayList<>();
        for (Orden orden : ordenes(salida, AccionSwitch.ON)) {
            Habitacion hab = porId.get(orden.idHabitacion());
            if (potenciaOcupada(habitaciones, estadoFinal, hab.id()) + hab.potenciaKW() > contratada + TOLERANCIA_KW) {
                // El core contó con la potencia de un apagado que no se pudo confirmar: encender ahora pasaría
                // la contratada. Se reintenta en el ciclo siguiente, con el estado real de los switches.
                postergadas.add(orden.idHabitacion());
                continue;
            }
            encendidos.add(orden);
            estadoFinal.put(orden.idHabitacion(), EstadoSwitch.DESCONOCIDO); // ya cuenta para los siguientes
        }
        comandar(encendidos, porId, estadoFinal, cambios);
        avisarPostergadas(postergadas, ahora);
        resumirSiHuboCambios(ahora, cambios, estadoFinal, habitaciones, contratada);
    }

    private static void resumirSiHuboCambios(ZonedDateTime ahora, List<String> cambios,
            Map<String, EstadoSwitch> estadoFinal, List<HabitacionDTO> habitaciones, double contratada) {
        if (!cambios.isEmpty()) {
            resumirCiclo(ahora, cambios, estadoFinal, habitaciones, contratada);
        }
    }

    private static List<Orden> ordenes(Salida salida, AccionSwitch accion) {
        return salida.ordenes().stream().filter(orden -> orden.accion() == accion).toList();
    }

    /**
     * Envía las órdenes a la vez y anota cómo queda cada switch. Lo que no se pudo confirmar queda desconocido:
     * no se sabe si el comando llegó, y lo que no se pudo apagar se cuenta como encendido.
     */
    private void comandar(List<Orden> ordenes, Map<String, Habitacion> porId, Map<String, EstadoSwitch> estadoFinal,
            List<String> cambios) {
        List<Resultado> resultados = enParalelo(ordenes, orden -> {
            if (!enEjecucion) {
                return null; // el control se detuvo: esta orden no sale
            }
            Habitacion hab = porId.get(orden.idHabitacion());
            return switchClient.comandarSwitch(hab.urlSwitch(), hab.idSwitch(), orden.accion().name());
        });
        for (int i = 0; i < ordenes.size(); i++) {
            Orden orden = ordenes.get(i);
            if (resultados.get(i) == null) {
                continue;
            }
            boolean encender = orden.accion() == AccionSwitch.ON;
            if (resultados.get(i) == Resultado.APLICADO) {
                cambios.add(orden.idHabitacion() + " → " + orden.accion());
                estadoFinal.put(orden.idHabitacion(), encender ? EstadoSwitch.ON : EstadoSwitch.OFF);
                if (encender && fallas.termino(FALLA_ON_RECHAZADO + orden.idHabitacion())) {
                    LOG.info("El switch de {} vuelve a confirmar el ON", orden.idHabitacion());
                }
            } else {
                estadoFinal.put(orden.idHabitacion(), EstadoSwitch.DESCONOCIDO);
                if (encender) {
                    rechazoElOn(orden.idHabitacion());
                }
            }
        }
    }

    private void rechazoElOn(String idHabitacion) {
        onRechazado.put(idHabitacion, CICLOS_SIN_REINTENTAR_ON);
        if (fallas.empezo(FALLA_ON_RECHAZADO + idHabitacion)) {
            LOG.warn("El switch de {} no confirmó el ON: mientras siga apagado, se deja fuera del reparto {} ciclos "
                    + "para que la potencia vaya a otras habitaciones, y se reintenta", idHabitacion,
                    CICLOS_SIN_REINTENTAR_ON);
        }
    }

    /**
     * Lo que se le pasa al core: todas las habitaciones menos las que tienen un switch que rechazó el {@code ON} hace
     * menos de {@link #CICLOS_SIN_REINTENTAR_ON} ciclos y sigue apagado. Si quedó encendido o en un estado
     * desconocido (por ejemplo, el {@code ON} llegó pero la respuesta se perdió), sigue en el reparto: el core tiene
     * que contar su potencia y poder apagarlo.
     */
    private List<HabitacionDTO> sinLasQueRechazaronElOn(List<HabitacionDTO> habitaciones) {
        if (onRechazado.isEmpty()) {
            return habitaciones;
        }
        List<HabitacionDTO> enElReparto = habitaciones.stream()
                .filter(h -> h.estadoActual() != EstadoSwitch.OFF || !onRechazado.containsKey(h.id()))
                .toList();
        onRechazado.replaceAll((id, ciclos) -> ciclos - 1);
        onRechazado.values().removeIf(ciclos -> ciclos <= 0);
        return enElReparto;
    }

    /**
     * Aplica {@code tarea} a cada elemento, todos a la vez, y devuelve los resultados en el mismo orden. Con los
     * switches, cada tanda tarda lo que el más lento (como mucho, un timeout) y no la suma de todos: con 10 switches
     * colgados y llamadas de a una, consultar y apagar dejaba el control ciego 20 timeouts (minutos simulados con
     * ×60).
     */
    private static <T, R> List<R> enParalelo(List<T> elementos, Function<T, R> tarea) {
        if (elementos.size() < 2) {
            return elementos.stream().map(tarea).toList();
        }
        try (ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<R>> pendientes = elementos.stream()
                    .map(elemento -> CompletableFuture.supplyAsync(() -> tarea.apply(elemento), hilos))
                    .toList();
            return pendientes.stream().map(CompletableFuture::join).toList();
        }
    }

    /**
     * Potencia que puede estar en uso sin contar a {@code excepto}: la de las habitaciones encendidas y la de las
     * que tienen un estado desconocido (por ejemplo, un apagado que no se pudo confirmar).
     */
    private static double potenciaOcupada(List<HabitacionDTO> habitaciones, Map<String, EstadoSwitch> estado,
            String excepto) {
        return habitaciones.stream()
                .filter(h -> !h.id().equals(excepto) && estado.get(h.id()) != EstadoSwitch.OFF)
                .mapToDouble(HabitacionDTO::potenciaKW)
                .sum();
    }

    /** Avisa una vez cuando se empiezan a postergar encendidos y otra cuando se vuelve a encender normalmente. */
    private void avisarPostergadas(List<String> postergadas, ZonedDateTime ahora) {
        if (postergadas.isEmpty()) {
            if (fallas.termino(FALLA_ON_POSTERGADO)) {
                LOG.info("[simulado {}] Los apagados se vuelven a confirmar: se enciende lo que entra",
                        horaSimulada(ahora));
            }
            return;
        }
        if (fallas.empezo(FALLA_ON_POSTERGADO)) {
            LOG.warn("[simulado {}] No se enciende {}: un apagado no se pudo confirmar y esa potencia sigue "
                    + "ocupada (se reintenta en cada ciclo)", horaSimulada(ahora), String.join(", ", postergadas));
        }
    }

    /**
     * Una línea por ciclo con cambios: hora simulada, qué se encendió o apagó, y cómo queda repartida la
     * potencia. Ej.: {@code [simulado lun 26/10 17:00] room1 → OFF | en uso 0.8 de 3.7 kW: room2}.
     */
    private static void resumirCiclo(ZonedDateTime ahora, List<String> cambios, Map<String, EstadoSwitch> estadoFinal,
            List<HabitacionDTO> habitaciones, double potenciaContratada) {
        List<String> encendidas = new ArrayList<>();
        double enUso = 0;
        for (HabitacionDTO h : habitaciones) {
            if (estadoFinal.get(h.id()) == EstadoSwitch.ON) {
                encendidas.add(h.id());
                enUso += h.potenciaKW();
            }
        }
        LOG.info("[simulado {}] {} | en uso {} de {} kW: {}", horaSimulada(ahora), String.join(", ", cambios),
                kw(enUso), kw(potenciaContratada), encendidas.isEmpty() ? "ninguna" : String.join(", ", encendidas));
    }

    /** Avisa solo cuando cambia el conjunto de habitaciones sin lecturas, no en cada ciclo. */
    private void avisarSinLecturas(List<String> sinLecturas, ZonedDateTime ahora) {
        if (sinLecturas.equals(ultimoSinLecturas)) {
            return;
        }
        if (sinLecturas.isEmpty()) {
            LOG.info("[simulado {}] Volvieron las lecturas de todas las habitaciones", horaSimulada(ahora));
        } else {
            LOG.warn("[simulado {}] Fail-safe: sin lecturas recientes de {}; se apagan por seguridad",
                    horaSimulada(ahora), String.join(", ", sinLecturas));
        }
        ultimoSinLecturas = List.copyOf(sinLecturas);
    }

    /** Hora simulada legible en hora local del sitio, por ejemplo {@code lun 26/10 17:00}. */
    static String horaSimulada(ZonedDateTime instante) {
        ZonedDateTime local = instante.withZoneSameInstant(RelojControl.ZONA_LOCAL);
        return DIAS[local.getDayOfWeek().getValue() - 1] + " " + DIA_HORA.format(local);
    }

    private static String kw(double valor) {
        return String.format(Locale.ROOT, "%.1f", valor);
    }

    /**
     * Temperatura de la última lectura, o {@code null} si no hay o el termostato está callado hace demasiado.
     *
     * <p>Se mide cuánto tiempo real pasó desde que llegó la lectura, no su {@code ts}: así no importa en qué
     * reloj venga el {@code ts}. El límite está en {@link #silencioMaximo(Duration)}.
     */
    private Double temperaturaVigente(Lectura lectura) {
        if (lectura == null) {
            return null;
        }
        Duration silencio = Duration.between(lectura.recibidaEn(), reloj.ahoraReal());
        if (silencio.compareTo(silencioMaximo(lectura.ritmo())) > 0) {
            return null;
        }
        return lectura.temperaturaC();
    }

    /**
     * Cuánto tiempo real puede callar un termostato que publica cada {@code ritmo} antes de darlo por caído: lo
     * mayor entre {@link #LECTURA_VENCIDA} simulados llevados a tiempo real y {@link #LECTURAS_PERDIDAS}
     * lecturas perdidas, con un piso de {@link #SILENCIO_MINIMO_REAL}. Antes de detectar la falta de una
     * lectura hay que esperar a que le toque llegar: con aceleración alta, esa espera manda.
     */
    Duration silencioMaximo(Duration ritmo) {
        Duration porLecturasPerdidas = (ritmo != null ? ritmo : RITMO_SUPUESTO).multipliedBy(LECTURAS_PERDIDAS);
        return Collections.max(List.of(reloj.aTiempoReal(LECTURA_VENCIDA), porLecturasPerdidas,
                SILENCIO_MINIMO_REAL));
    }

    static TarifaPunta tarifaPunta(Franja franja) {
        return new TarifaPunta(LocalTime.parse(franja.desde()), LocalTime.parse(franja.hasta()), dias(franja.dias()));
    }

    private static Set<DayOfWeek> dias(String dias) {
        return switch (dias) {
            case "HABILES" -> EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
            case "TODOS" -> EnumSet.allOf(DayOfWeek.class);
            default -> {
                LOG.warn("Días de tarifa punta desconocidos '{}': no se aplicará la punta. Recargá el sitio.", dias);
                yield EnumSet.noneOf(DayOfWeek.class);
            }
        };
    }

    private void cancelarTarea() {
        if (tareaProgramada != null) {
            tareaProgramada.cancel(false);
            tareaProgramada = null;
        }
    }
}
