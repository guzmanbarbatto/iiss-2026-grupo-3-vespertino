package com.ioteste.api.Domain;

public class ValidacionResponse {
    private boolean consistente;
    private String mensaje;
    
    public ValidacionResponse(boolean consistente, String mensaje) {
        this.consistente = consistente;
        this.mensaje = mensaje;
    }
    
    public boolean isConsistente() { return consistente; }
    public void setConsistente(boolean consistente) { this.consistente = consistente; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
}