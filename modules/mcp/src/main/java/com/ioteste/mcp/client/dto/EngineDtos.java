package com.ioteste.mcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * DTOs del contrato {@code controlador.openapi-v2.yaml} (API REST del engine).
 *
 * <p>Se duplican deliberadamente aquí en lugar de depender del jar del engine: el MCP es un cliente
 * más del engine y solo conoce su contrato público.
 */
public final class EngineDtos {

    private EngineDtos() {
    }

    /** Inventario completo del sitio ({@code GET /sitio}). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Inventario(String schemaVersion, Sitio sitio, List<Habitacion> habitaciones) {
    }

    /** Datos generales del sitio. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sitio(String id, String nombre, double potenciaContratadaKW, Tarifa tarifa) {
    }

    /** Tarifa eléctrica del sitio. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tarifa(TarifaPunta punta) {
    }

    /** Franja de tarifa punta. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TarifaPunta(String desde, String hasta, String dias) {
    }

    /** Habitación del sitio con su termostato y su switch. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Habitacion(
            String id,
            String nombre,
            double temperaturaEsperada,
            double potenciaKW,
            String idTermostato,
            String topicTermostato,
            String idSwitch,
            String urlSwitch) {
    }

    /** Estado del control ({@code GET /control}). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EstadoControl(String estado, String fechaHora, Double factorTiempo, String iniciadoEn) {

        public boolean enEjecucion() {
            return "EN_EJECUCION".equals(estado);
        }
    }
}

