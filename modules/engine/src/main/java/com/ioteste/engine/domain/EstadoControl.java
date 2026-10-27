package com.ioteste.engine.domain;

public record EstadoControl(
    String estado,
    String fechaHora,
    Double factorTiempo,
    String iniciadoEn
) {}