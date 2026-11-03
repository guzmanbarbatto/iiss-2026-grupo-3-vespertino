package com.ioteste.engine.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mensaje que publica un termostato por MQTT. Se ignoran los campos que no se usan para que un
 * campo extra en el payload no haga descartar la lectura. {@code ts} (epoch en segundos) puede faltar.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MensajeTermostato(
    Double tC,
    Double ts
) { }
