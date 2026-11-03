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
        return aLogico(clock.instant());
    }

    /** Lleva un instante del reloj real al reloj lógico. Detenido, lo devuelve sin cambios. */
    public ZonedDateTime aLogico(Instant real) {
        return aLogico(sincronizacion, real);
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
