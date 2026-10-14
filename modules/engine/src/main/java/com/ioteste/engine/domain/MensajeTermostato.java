package com.ioteste.engine.domain;

public record MensajeTermostato(
    int id,
    double tC,
    Double tF,
    double ts
) {}