package com.ioteste.engine.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Respuesta de {@code GET /switches/{idSwitch}} del simulador. {@code estado}: ON, OFF o DESCONOCIDO. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RespuestaEstadoSwitch(
    String idSwitch,
    String estado
) { }
