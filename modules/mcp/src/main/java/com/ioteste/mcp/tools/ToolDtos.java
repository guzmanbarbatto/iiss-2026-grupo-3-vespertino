package com.ioteste.mcp.tools;

import java.util.List;

/** Respuestas que devuelven las tools MCP (se serializan a JSON para el agente). */
public final class ToolDtos {

    private ToolDtos() {
    }

    /** Foto del estado actual de la casa. */
    public record EstadoHabitaciones(
            String sitioId,
            String sitioNombre,
            double potenciaContratadaKW,
            double potenciaEnUsoKW,
            double potenciaDisponibleKW,
            List<EstadoHabitacion> habitaciones,
            List<String> advertencias) {
    }

    /**
     * Estado de una habitación. {@code temperaturaActual}, {@code ultimaLecturaEn} y
     * {@code necesitaCalor} son {@code null} si el termostato todavía no reportó ninguna lectura.
     * {@code estadoSwitch} es ON, OFF, DESCONOCIDO o NO_DISPONIBLE (si falló la consulta).
     */
    public record EstadoHabitacion(
            String id,
            String nombre,
            Double temperaturaActual,
            String ultimaLecturaEn,
            double temperaturaEsperada,
            Boolean necesitaCalor,
            double potenciaKW,
            String idSwitch,
            String estadoSwitch) {
    }

    /** Historial de lecturas de una habitación, de la más reciente a la más antigua. */
    public record HistorialTemperaturas(
            String habitacionId,
            String nombre,
            double temperaturaEsperada,
            int cantidad,
            List<LecturaTemperatura> lecturas) {
    }

    /** Una lectura puntual del termostato. */
    public record LecturaTemperatura(String fechaHora, double temperaturaC) {
    }

    /** Resultado de comandar manualmente un switch. */
    public record ResultadoAccionSwitch(
            String habitacionId,
            String idSwitch,
            String estado,
            boolean cambio,
            String aplicadoEn,
            String advertencia) {
    }
}

