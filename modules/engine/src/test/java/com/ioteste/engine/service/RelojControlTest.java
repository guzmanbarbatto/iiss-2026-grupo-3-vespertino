package com.ioteste.engine.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ioteste.engine.RelojMutable;
import com.ioteste.engine.domain.EstadoControl;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RelojControlTest {

    private static final Instant REAL = Instant.parse("2026-11-03T15:00:00Z");
    private static final Instant LOGICO = Instant.parse("2026-12-24T21:00:00Z");

    private final RelojMutable clock = new RelojMutable(REAL);
    private final RelojControl reloj = new RelojControl(clock);

    @Test
    void detenidoUsaLaHoraDelSistemaYReportaNulls() {
        assertThat(reloj.ahora().toInstant()).isEqualTo(REAL);
        assertThat(reloj.ahora().getZone()).isEqualTo(RelojControl.ZONA_LOCAL);
        assertThat(reloj.estado()).isEqualTo(new EstadoControl("DETENIDO", null, null, null));
    }

    @Test
    void sincronizadoAvanzaSegunElFactor() {
        reloj.sincronizar(LOGICO, 10.0);
        clock.avanzar(Duration.ofSeconds(3));

        assertThat(reloj.ahora().toInstant()).isEqualTo(LOGICO.plusSeconds(30));
        assertThat(reloj.estado()).isEqualTo(new EstadoControl(
                "EN_EJECUCION", "2026-12-24T21:00:30.000Z", 10.0, "2026-11-03T15:00:00.000Z"));
    }

    @Test
    void convierteInstantesRealesAlRelojLogico() {
        reloj.sincronizar(LOGICO, 60.0);

        assertThat(reloj.aLogico(REAL.plusSeconds(1)).toInstant()).isEqualTo(LOGICO.plusSeconds(60));
    }

    @Test
    void resincronizarYDetener() {
        reloj.sincronizar(LOGICO, 10.0);
        clock.avanzar(Duration.ofSeconds(5));
        reloj.sincronizar(LOGICO, 1.0);

        assertThat(reloj.ahora().toInstant()).isEqualTo(LOGICO);

        reloj.detener();
        assertThat(reloj.estado().estado()).isEqualTo("DETENIDO");
    }
}
