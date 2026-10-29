package com.ioteste.mcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** DTOs del contrato {@code simulador.openapi-v2.yaml} usados por el MCP (switches). */
public final class SimuladorDtos {

    private SimuladorDtos() {
    }

    /** Cuerpo de {@code POST /switches/{idSwitch}/comandos}. */
    public record ComandoSwitch(String accion) {
    }

    /** Respuesta de {@code GET /switches/{idSwitch}}. Estado: ON, OFF o DESCONOCIDO. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EstadoSwitch(String idSwitch, String estado, String actualizadoEn) {
    }

    /** Respuesta de {@code POST /switches/{idSwitch}/comandos}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RespuestaComando(String idSwitch, String estado, boolean cambio, String aplicadoEn) {
    }
}

