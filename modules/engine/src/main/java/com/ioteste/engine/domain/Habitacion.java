package com.ioteste.engine.domain;

public record Habitacion(
    String id,
    String nombre,
    double temperaturaEsperada,
    double potenciaKW,
    String idTermostato,
    String topicTermostato,
    String idSwitch,
    String urlSwitch
) {}