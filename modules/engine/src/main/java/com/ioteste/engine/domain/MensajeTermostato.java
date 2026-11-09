package com.ioteste.engine.domain;

/**
 * Lectura válida de un termostato (§2): la temperatura en °C y, si vino, el {@code ts} del mensaje (epoch en
 * segundos). La valida {@code MqttSubscriberService}: un mensaje fuera del contrato nunca llega a ser una lectura.
 */
public record MensajeTermostato(
    double tC,
    Double ts
) { }
