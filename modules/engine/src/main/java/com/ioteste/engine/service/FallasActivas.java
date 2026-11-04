package com.ioteste.engine.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recuerda qué fallas están en curso para avisar una sola vez cuando empiezan y una vez cuando terminan.
 * Sin esto, una caída del simulador o de la base repetía el mismo aviso en cada ciclo de control (con
 * factor x60, varias veces por segundo) y tapaba el resto del log.
 */
final class FallasActivas {

    private final Set<String> activas = ConcurrentHashMap.newKeySet();

    /** Registra una falla. Devuelve {@code true} solo si recién empieza (hay que avisar). */
    boolean empezo(String clave) {
        return activas.add(clave);
    }

    /** Registra que algo funcionó. Devuelve {@code true} solo si venía fallando (hay que avisar). */
    boolean termino(String clave) {
        return activas.remove(clave);
    }
}
