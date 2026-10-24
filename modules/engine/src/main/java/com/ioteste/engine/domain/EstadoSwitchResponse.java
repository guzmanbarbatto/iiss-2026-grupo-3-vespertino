package com.ioteste.engine.domain;

public record EstadoSwitchResponse(
        String idSwitch,
        String estado,
        String actualizadoEn
) {}
