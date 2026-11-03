package com.ioteste.engine.domain;

/** Los numéricos son objetos para poder distinguir un campo ausente en el {@code PUT /sitio}. */
public record Habitacion(
    String id,
    String nombre,
    Double temperaturaEsperada,
    Double potenciaKW,
    String idTermostato,
    String topicTermostato,
    String idSwitch,
    String urlSwitch
) { }
