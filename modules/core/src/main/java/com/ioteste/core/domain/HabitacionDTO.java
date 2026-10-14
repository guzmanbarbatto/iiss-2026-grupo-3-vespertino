package com.ioteste.core.domain;

// Representa la HabitacionDTO del diagrama
public record HabitacionDTO(
    String id, 
    double temperaturaEsperada, 
    double temperaturaActual, 
    double potenciaKW, 
    EstadoSwitch estadoActual
) {}