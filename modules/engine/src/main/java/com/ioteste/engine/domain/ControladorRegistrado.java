package com.ioteste.engine.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Registro de un controlador en el simulador ({@code POST} y {@code GET /controladores}). Solo la
 * respuesta del alta trae {@code mqtt}: el broker al que hay que conectarse.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ControladorRegistrado(
    String etiqueta,
    BrokerMqtt mqtt
) {

    /** Broker MQTT que anuncia el simulador. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BrokerMqtt(String host, Integer puerto) {

        public String url() {
            return "tcp://" + host + ":" + puerto;
        }
    }
}
