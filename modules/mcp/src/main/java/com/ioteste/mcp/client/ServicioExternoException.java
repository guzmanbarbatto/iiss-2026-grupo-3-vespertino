package com.ioteste.mcp.client;

/**
 * Error al comunicarse con un servicio externo (engine, simulador o base de datos).
 *
 * <p>El mensaje está pensado para ser leído por el agente de IA que invocó la tool, por lo que
 * debe explicar qué falló y, si es posible, cómo resolverlo.
 */
public class ServicioExternoException extends RuntimeException {

    public ServicioExternoException(String mensaje) {
        super(mensaje);
    }

    public ServicioExternoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

