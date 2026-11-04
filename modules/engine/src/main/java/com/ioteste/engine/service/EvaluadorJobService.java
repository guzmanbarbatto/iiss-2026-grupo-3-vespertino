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
import java.util.concurrent.ScheduledFuture;
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

    private ScheduledFuture<?> tareaProgramada;
    private final FallasActivas fallas = new FallasActivas();
    private volatile List<String> ultimoSinLecturas = List.of();

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
        long intervaloReal = Math.max(INTERVALO_MINIMO_REAL_MS, (long) (INTERVALO_LOGICO_MS / factorTiempo));
        tareaProgramada = scheduler.scheduleAtFixedRate(this::ejecutarCicloDeControl, Duration.ofMillis(intervaloReal));
        LOG.info("Control iniciado: hora simulada {} (×{}, un ciclo cada {} ms)",
                horaSimulada(fechaHora.atZone(RelojControl.ZONA_LOCAL)),
                BigDecimal.valueOf(factorTiempo).stripTrailingZeros().toPlainString(), intervaloReal);
        return reloj.estado();
    }

    /** Detiene el control. Es idempotente y no apaga lo que haya quedado encendido. */
    public synchronized EstadoControl detenerMotor() {
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
        apagarSwitchesRetirados(ahora);
        if (inventario.isEmpty()) {
            if (fallas.empezo(FALLA_SIN_SITIO)) {
                LOG.warn("No hay un sitio cargado: el control espera el PUT /sitio del simulador");
            }
            return;
        }
        fallas.termino(FALLA_SIN_SITIO);
        evaluar(inventario.get(), ahora);
    }

    /**
     * Apaga los switches de las habitaciones que salieron del inventario: si no, seguirían encendidos sin que
     * nadie los controle y fuera de la cuenta de potencia. Los pendientes están en la base (los anota el
     * {@code PUT /sitio}), así que sobreviven a un reinicio del engine. Se reintenta en cada ciclo hasta
     * lograrlo; si el switch ya no existe en el simulador, no hay nada que apagar.
     */
    private void apagarSwitchesRetirados(ZonedDateTime ahora) {
        for (String idSwitch : sitioRepository.switchesPendientesDeApagar()) {
            Resultado resultado = switchClient.comandarSwitch(idSwitch, AccionSwitch.OFF.name());
            if (resultado == Resultado.APLICADO) {
                sitioRepository.quitarPendienteDeApagar(idSwitch);
                LOG.info("[simulado {}] {} → OFF (su habitación ya no está en el inventario)",
                        horaSimulada(ahora), idSwitch);
            } else if (resultado == Resultado.INEXISTENTE) {
                sitioRepository.quitarPendienteDeApagar(idSwitch);
                LOG.info("{} salió del inventario y no existe en el simulador: no hay nada que apagar", idSwitch);
            }
        }
    }

    private void evaluar(InventarioDTO inventario, ZonedDateTime ahora) {
        Map<String, Lectura> lecturas = historicoRepository.ultimasLecturas();
        Map<String, String> switchPorHabitacion = new HashMap<>();
        List<HabitacionDTO> habitaciones = new ArrayList<>();
        List<String> sinLecturasVigentes = new ArrayList<>();

        for (Habitacion hab : inventario.habitaciones()) {
            switchPorHabitacion.put(hab.id(), hab.idSwitch());
            Double temperatura = temperaturaVigente(lecturas.get(hab.id()));
            if (temperatura == null) {
                sinLecturasVigentes.add(hab.id());
                temperatura = TEMPERATURA_FAIL_SAFE;
            }
            habitaciones.add(new HabitacionDTO(hab.id(), hab.temperaturaEsperada(), temperatura, hab.potenciaKW(),
                    switchClient.consultarEstado(hab.idSwitch())));
        }

        avisarSinLecturas(sinLecturasVigentes, ahora);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Ciclo {} | potencia contratada {} kW | {}", ahora,
                    inventario.sitio().potenciaContratadaKW(), habitaciones);
        }

        Salida salida = evaluadorCore.evaluarEstado(ahora, inventario.sitio().potenciaContratadaKW(),
                tarifaPunta(inventario.sitio().tarifa().punta()), habitaciones);

        Map<String, EstadoSwitch> estadoFinal = new LinkedHashMap<>();
        habitaciones.forEach(h -> estadoFinal.put(h.id(), h.estadoActual()));
        List<String> cambios = new ArrayList<>();
        for (Orden orden : salida.ordenes()) {
            String idSwitch = switchPorHabitacion.get(orden.idHabitacion());
            if (switchClient.comandarSwitch(idSwitch, orden.accion().name()) == Resultado.APLICADO) {
                cambios.add(orden.idHabitacion() + " → " + orden.accion());
                estadoFinal.put(orden.idHabitacion(),
                        orden.accion() == AccionSwitch.ON ? EstadoSwitch.ON : EstadoSwitch.OFF);
            }
        }
        if (!cambios.isEmpty()) {
            resumirCiclo(ahora, cambios, estadoFinal, habitaciones, inventario.sitio().potenciaContratadaKW());
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
