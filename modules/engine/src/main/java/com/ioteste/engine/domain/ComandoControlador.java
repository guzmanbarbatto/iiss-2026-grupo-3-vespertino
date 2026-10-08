package com.ioteste.engine.domain;

public class ComandoControlador {
    private String accion;
    
    // TO-DO: Agregado para cumplir con la recepción de la fecha/hora de sincronización.
    private String fechaSincronizacion; 

    public ComandoControlador() {
    }

    public String getAccion() { 
        return accion; 
    }
    
    public void setAccion(String accion) { 
        this.accion = accion; 
    }

    public String getFechaSincronizacion() {
        return fechaSincronizacion;
    }

    public void setFechaSincronizacion(String fechaSincronizacion) {
        this.fechaSincronizacion = fechaSincronizacion;
    }
}