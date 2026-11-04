package com.ioteste.engine.service;

import com.ioteste.engine.domain.ErrorRespuesta;
import com.ioteste.engine.domain.EstadoControl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

/**
 * Reloj lógico del control: {@code ahora = fechaHora + tiempoRealTranscurrido × factorTiempo}.
 *
 * <p>Toda la sincronización vive en un único objeto inmutable publicado en un campo {@code volatile}:
 * quien lo lee ve siempre un estado completo (nunca, por ejemplo, el inicio real sin el lógico).
 */
@Component
public class RelojControl {

    /** La franja tarifaria se expresa en hora local del sitio. */
    public static final ZoneId ZONA_LOCAL = ZoneId.of("America/Montevideo");

    private record Sincronizacion(Instant inicioReal, Instant inicioLogico, double factor) { }

    private final Clock clock;
    private volatile Sincronizacion sincronizacion;

    public RelojControl(Clock clock) {
        this.clock = clock;
    }

    public void sincronizar(Instant fechaHora, double factorTiempo) {
        sincronizacion = new Sincronizacion(clock.instant(), fechaHora, factorTiempo);
    }

    public void detener() {
        sincronizacion = null;
    }

    /** Hora lógica actual, en hora local del sitio. Detenido, es la hora del sistema. */
    public ZonedDateTime ahora() {
        return aLogico(sincronizacion, clock.instant());
    }

    /** Instante real (del sistema). */
    public Instant ahoraReal() {
        return clock.instant();
    }

    /** Cuánto tiempo real equivale a una duración lógica con el factor vigente. Detenido, es la misma. */
    public Duration aTiempoReal(Duration logica) {
        Sincronizacion s = sincronizacion;
        return s == null ? logica : Duration.ofMillis((long) (logica.toMillis() / s.factor()));
    }

    /** Estado del control según el contrato: detenido ⇒ todos los campos opcionales en {@code null}. */
    public EstadoControl estado() {
        Sincronizacion s = sincronizacion;
        if (s == null) {
            return new EstadoControl("DETENIDO", null, null, null);
        }
        return new EstadoControl(
                "EN_EJECUCION",
                ErrorRespuesta.ISO_UTC.format(aLogico(s, clock.instant())),
                s.factor(),
                ErrorRespuesta.ISO_UTC.format(s.inicioReal()));
    }

    private static ZonedDateTime aLogico(Sincronizacion s, Instant real) {
        if (s == null) {
            return real.atZone(ZONA_LOCAL);
        }
        long millisReales = Duration.between(s.inicioReal(), real).toMillis();
        return s.inicioLogico().plusMillis((long) (millisReales * s.factor())).atZone(ZONA_LOCAL);
    }
}
