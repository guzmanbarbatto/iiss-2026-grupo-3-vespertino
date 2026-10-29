package com.ioteste.mcp.tools;

import com.ioteste.mcp.client.EngineClient;
import com.ioteste.mcp.client.ServicioExternoException;
import com.ioteste.mcp.client.SimuladorClient;
import com.ioteste.mcp.client.dto.EngineDtos.EstadoControl;
import com.ioteste.mcp.client.dto.EngineDtos.Habitacion;
import com.ioteste.mcp.client.dto.EngineDtos.Inventario;
import com.ioteste.mcp.client.dto.SimuladorDtos.RespuestaComando;
import com.ioteste.mcp.repository.TemperaturaRepository;
import com.ioteste.mcp.repository.TemperaturaRepository.Lectura;
import com.ioteste.mcp.tools.ToolDtos.EstadoHabitacion;
import com.ioteste.mcp.tools.ToolDtos.EstadoHabitaciones;
import com.ioteste.mcp.tools.ToolDtos.HistorialTemperaturas;
import com.ioteste.mcp.tools.ToolDtos.LecturaTemperatura;
import com.ioteste.mcp.tools.ToolDtos.ResultadoAccionSwitch;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tools MCP de EcoWarm.
 *
 * <p>Combina tres fuentes: la API REST del engine (sitio y control), la base de datos en modo solo
 * lectura (temperaturas) y la API del simulador (switches). Los errores se lanzan como excepciones
 * con mensajes en español; Spring AI los devuelve al cliente MCP como resultado con
 * {@code isError = true}.
 */
@Component
public class EcoWarmTools {

    static final int LIMITE_HISTORIAL_POR_DEFECTO = 20;
    static final int LIMITE_HISTORIAL_MAXIMO = 500;

    static final String SWITCH_ON = "ON";
    static final String SWITCH_NO_DISPONIBLE = "NO_DISPONIBLE";

    private final EngineClient engineClient;
    private final SimuladorClient simuladorClient;
    private final TemperaturaRepository temperaturaRepository;

    public EcoWarmTools(
            EngineClient engineClient,
            SimuladorClient simuladorClient,
            TemperaturaRepository temperaturaRepository) {
        this.engineClient = engineClient;
        this.simuladorClient = simuladorClient;
        this.temperaturaRepository = temperaturaRepository;
    }

    // ------------------------------------------------------------------
    // Sitio y habitaciones
    // ------------------------------------------------------------------

    @Tool(description = """
            Devuelve la configuración completa del sitio cargado en el controlador EcoWarm: nombre, \
            potencia contratada (kW), franja de tarifa punta (desde, hasta, días) y la lista de \
            habitaciones con su temperatura objetivo, potencia de calefacción, termostato y switch.""")
    public Inventario obtenerSitio() {
        return sitioCargado();
    }

    @Tool(description = """
            Consulta el estado actual de las habitaciones: temperatura actual (última lectura del \
            termostato), temperatura objetivo, si necesita calor, estado del switch de calefacción \
            (ON, OFF, DESCONOCIDO o NO_DISPONIBLE) y la potencia en uso frente a la contratada. \
            Si se indica habitacionId devuelve solo esa habitación.""")
    public EstadoHabitaciones consultarEstadoHabitaciones(
            @ToolParam(required = false,
                    description = "Id de la habitación (por ejemplo hab-1). Vacío para todas.")
            String habitacionId) {

        Inventario inventario = sitioCargado();
        List<Habitacion> todas = habitacionesDe(inventario);
        if (tieneTexto(habitacionId)) {
            buscarHabitacion(inventario, habitacionId);
        }

        List<String> advertencias = new ArrayList<>();
        Map<String, Lectura> lecturas = Map.of();
        try {
            lecturas = temperaturaRepository.ultimasLecturas();
        } catch (ServicioExternoException e) {
            advertencias.add(e.getMessage());
        }

        double potenciaEnUso = 0.0;
        List<EstadoHabitacion> estados = new ArrayList<>();
        for (Habitacion hab : todas) {
            String estadoSwitch = estadoSwitch(hab, advertencias);
            if (SWITCH_ON.equals(estadoSwitch)) {
                potenciaEnUso += hab.potenciaKW();
            }
            if (!tieneTexto(habitacionId) || hab.id().equals(habitacionId)) {
                estados.add(estadoHabitacion(hab, lecturas.get(hab.id()), estadoSwitch));
            }
        }

        for (EstadoHabitacion e : estados) {
            if (e.temperaturaActual() == null) {
                advertencias.add("La habitación " + e.id() + " no tiene lecturas de temperatura registradas.");
            }
        }

        double contratada = inventario.sitio().potenciaContratadaKW();
        return new EstadoHabitaciones(
                inventario.sitio().id(),
                inventario.sitio().nombre(),
                contratada,
                potenciaEnUso,
                contratada - potenciaEnUso,
                estados,
                advertencias);
    }

    @Tool(description = """
            Devuelve el historial de temperaturas registradas por el termostato de una habitación, \
            de la lectura más reciente a la más antigua.""")
    public HistorialTemperaturas historialTemperaturas(
            @ToolParam(description = "Id de la habitación (por ejemplo hab-1).") String habitacionId,
            @ToolParam(required = false,
                    description = "Cantidad máxima de lecturas a devolver (1 a 500, por defecto 20).")
            Integer limite) {

        if (!tieneTexto(habitacionId)) {
            throw new IllegalArgumentException("Debe indicar habitacionId.");
        }
        int cantidad = limite == null ? LIMITE_HISTORIAL_POR_DEFECTO : limite;
        if (cantidad < 1 || cantidad > LIMITE_HISTORIAL_MAXIMO) {
            throw new IllegalArgumentException(
                    "El límite debe estar entre 1 y " + LIMITE_HISTORIAL_MAXIMO + " (recibido: " + cantidad + ").");
        }

        Habitacion hab = buscarHabitacion(sitioCargado(), habitacionId);
        List<LecturaTemperatura> lecturas = temperaturaRepository.historial(hab.id(), cantidad).stream()
                .map(l -> new LecturaTemperatura(l.fechaHora().toString(), l.temperaturaC()))
                .toList();

        return new HistorialTemperaturas(
                hab.id(), hab.nombre(), hab.temperaturaEsperada(), lecturas.size(), lecturas);
    }

    // ------------------------------------------------------------------
    // Control
    // ------------------------------------------------------------------

    @Tool(description = """
            Devuelve el estado del control automático de calefacción: EN_EJECUCION o DETENIDO, la \
            hora lógica a la que sincronizó (fechaHora), el factor de aceleración del tiempo y la \
            hora real en que se inició.""")
    public EstadoControl obtenerEstadoControl() {
        return engineClient.obtenerControl();
    }

    @Tool(description = """
            Inicia (o re-sincroniza si ya está corriendo) el control automático de calefacción. \
            El controlador evalúa cada 30 segundos de tiempo lógico, usando el reloj \
            ahora = fechaHora + tiempoReal * factorTiempo.""")
    public EstadoControl iniciarControl(
            @ToolParam(description = "Instante ISO-8601 al que se sincroniza el reloj del control, "
                    + "por ejemplo 2026-10-01T09:15:00.000Z o 2026-10-01T06:15:00-03:00.")
            String fechaHora,
            @ToolParam(required = false,
                    description = "Aceleración respecto del tiempo real (mayor que 0, por defecto 1).")
            Double factorTiempo) {

        if (!tieneTexto(fechaHora)) {
            throw new IllegalArgumentException("Debe indicar fechaHora en formato ISO-8601.");
        }
        try {
            ZonedDateTime.parse(fechaHora);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("fechaHora no es un instante ISO-8601 válido: " + fechaHora);
        }
        if (factorTiempo != null && !(factorTiempo > 0)) {
            throw new IllegalArgumentException("factorTiempo debe ser mayor que 0 (recibido: " + factorTiempo + ").");
        }
        return engineClient.iniciarControl(fechaHora, factorTiempo);
    }

    @Tool(description = """
            Detiene el control automático de calefacción. El controlador deja de comandar switches \
            pero no apaga lo que haya quedado encendido. Es idempotente.""")
    public EstadoControl detenerControl() {
        return engineClient.detenerControl();
    }

    // ------------------------------------------------------------------
    // Switches
    // ------------------------------------------------------------------

    @Tool(description = """
            Enciende o apaga manualmente el switch de calefacción de una habitación. Atención: si el \
            control automático está EN_EJECUCION puede revertir este cambio en su próximo ciclo; para \
            un control manual sostenido detené primero el control.""")
    public ResultadoAccionSwitch accionarSwitch(
            @ToolParam(description = "Id de la habitación (por ejemplo hab-1).") String habitacionId,
            @ToolParam(description = "true para encender la calefacción, false para apagarla.") boolean encender) {

        if (!tieneTexto(habitacionId)) {
            throw new IllegalArgumentException("Debe indicar habitacionId.");
        }
        Habitacion hab = buscarHabitacion(sitioCargado(), habitacionId);
        RespuestaComando respuesta = simuladorClient.comandarSwitch(hab.idSwitch(), encender);

        return new ResultadoAccionSwitch(
                hab.id(),
                respuesta.idSwitch(),
                respuesta.estado(),
                respuesta.cambio(),
                respuesta.aplicadoEn(),
                advertenciaControlAutomatico());
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private Inventario sitioCargado() {
        return engineClient.obtenerSitio().orElseThrow(() -> new IllegalStateException(
                "El controlador todavía no tiene un sitio cargado. El simulador debe enviarlo con PUT /sitio."));
    }

    private static List<Habitacion> habitacionesDe(Inventario inventario) {
        return inventario.habitaciones() == null ? List.of() : inventario.habitaciones();
    }

    private static Habitacion buscarHabitacion(Inventario inventario, String habitacionId) {
        List<Habitacion> habitaciones = habitacionesDe(inventario);
        return habitaciones.stream()
                .filter(h -> h.id().equals(habitacionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No existe la habitación '" + habitacionId
                        + "'. Habitaciones disponibles: "
                        + habitaciones.stream().map(Habitacion::id).collect(Collectors.joining(", "))));
    }

    private String estadoSwitch(Habitacion hab, List<String> advertencias) {
        try {
            String estado = simuladorClient.consultarSwitch(hab.idSwitch()).estado();
            return estado == null ? SWITCH_NO_DISPONIBLE : estado;
        } catch (ServicioExternoException e) {
            advertencias.add(e.getMessage());
            return SWITCH_NO_DISPONIBLE;
        }
    }

    private static EstadoHabitacion estadoHabitacion(Habitacion hab, Lectura lectura, String estadoSwitch) {
        Double actual = lectura == null ? null : lectura.temperaturaC();
        return new EstadoHabitacion(
                hab.id(),
                hab.nombre(),
                actual,
                lectura == null ? null : lectura.fechaHora().toString(),
                hab.temperaturaEsperada(),
                actual == null ? null : actual < hab.temperaturaEsperada(),
                hab.potenciaKW(),
                hab.idSwitch(),
                estadoSwitch);
    }

    private String advertenciaControlAutomatico() {
        try {
            if (engineClient.obtenerControl().enEjecucion()) {
                return "El control automático está EN_EJECUCION y puede revertir este cambio en su próximo ciclo.";
            }
            return null;
        } catch (ServicioExternoException e) {
            return "No se pudo verificar si el control automático está activo: " + e.getMessage();
        }
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}

