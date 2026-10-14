package com.ioteste.engine.domain;

public record Sitio(
    String id, 
    String nombre, 
    double potenciaContratadaKW, 
    Tarifa tarifa
) {}