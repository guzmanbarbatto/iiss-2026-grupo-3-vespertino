package com.ioteste.core.domain;

/**
 * Estado conocido de un switch. {@code DESCONOCIDO} cubre tanto el estado que informa el simulador
 * mientras nadie accionó el relé como el caso en que no se pudo consultar: en ambos el controlador
 * no debe suponer nada y tiene que comandarlo explícitamente.
 */
public enum EstadoSwitch {
    ON,
    OFF,
    DESCONOCIDO
}
