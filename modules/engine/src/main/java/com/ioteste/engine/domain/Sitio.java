package com.ioteste.engine.domain;

/** Los numéricos son objetos para poder distinguir un campo ausente en el {@code PUT /sitio}. */
public record Sitio(
    String id,
    String nombre,
    Double potenciaContratadaKW,
    Tarifa tarifa
) { }
