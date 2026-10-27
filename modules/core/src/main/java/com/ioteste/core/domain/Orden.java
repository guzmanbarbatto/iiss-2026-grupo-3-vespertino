package com.ioteste.core.domain;

// Representa la Orden de encendido o apagado para el switch
public record Orden(
    String idHabitacion,
    AccionSwitch accion
) { }