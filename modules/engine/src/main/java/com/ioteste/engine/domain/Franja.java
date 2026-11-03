package com.ioteste.engine.domain;

/** Franja de tarifa punta en hora local, semiabierta {@code [desde, hasta)}. {@code dias}: HABILES o TODOS. */
public record Franja(
    String desde,
    String hasta,
    String dias
) { }
