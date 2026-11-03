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
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    /** Una lectura más vieja que esto (en tiempo lógico) se considera un termostato caído. */
    static final long LECTURA_VENCIDA_SEGUNDOS = 600;
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

    private ScheduledFuture<?> tareaProgramada;
    private volatile boolean avisoSinSitioDado;
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
            Optional<InventarioDTO> inventario = sitioRepository.obtenerInventario();
            if (inventario.isEmpty()) {
                // Se avisa una sola vez, no en cada ciclo (con factor alto serían varias líneas por segundo)
                if (!avisoSinSitioDado) {
                    LOG.warn("No hay un sitio cargado: el control espera el PUT /sitio del simulador");
                    avisoSinSitioDado = true;
                }
                return;
            }
            avisoSinSitioDado = false;
            evaluar(inventario.get(), reloj.ahora());
        } catch (RuntimeException e) {
            LOG.error("Error en el ciclo de control: {}", e.getMessage(), e);
        }
    }

    private void evaluar(InventarioDTO inventario, ZonedDateTime ahora) {
        Map<String, Lectura> lecturas = historicoRepository.ultimasLecturas();
        Map<String, String> switchPorHabitacion = new HashMap<>();
        List<HabitacionDTO> habitaciones = new ArrayList<>();
        List<String> sinLecturasVigentes = new ArrayList<>();

        for (Habitacion hab : inventario.habitaciones()) {
            switchPorHabitacion.put(hab.id(), hab.idSwitch());
            Double temperatura = temperaturaVigente(lecturas.get(hab.id()), ahora);
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
            if (switchClient.comandarSwitch(switchPorHabitacion.get(orden.idHabitacion()), orden.accion().name())) {
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
     * Temperatura de la última lectura, o {@code null} si no hay o está vencida.
     *
     * <p>El {@code ts} del termostato puede venir en el reloj virtual del simulador o en el real: se compara
     * contra la hora lógica de las dos formas y alcanza con que una la considere reciente.
     */
    private Double temperaturaVigente(Lectura lectura, ZonedDateTime ahora) {
        if (lectura == null) {
            return null;
        }
        long antiguedadLogica = Math.abs(Duration.between(lectura.fechaHora(), ahora.toInstant()).getSeconds());
        long antiguedadReal = Math.abs(Duration.between(
                reloj.aLogico(lectura.fechaHora()).toInstant(), ahora.toInstant()).getSeconds());
        if (Math.min(antiguedadLogica, antiguedadReal) > LECTURA_VENCIDA_SEGUNDOS) {
            return null;
        }
        return lectura.temperaturaC();
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
