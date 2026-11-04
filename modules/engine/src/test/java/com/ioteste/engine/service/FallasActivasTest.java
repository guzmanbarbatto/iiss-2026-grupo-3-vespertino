package com.ioteste.engine.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FallasActivasTest {

    private final FallasActivas fallas = new FallasActivas();

    @Test
    void unaFallaSeAvisaSoloCuandoEmpieza() {
        assertThat(fallas.empezo("sw-1")).isTrue();
        assertThat(fallas.empezo("sw-1")).isFalse();
        assertThat(fallas.empezo("sw-1")).isFalse();
    }

    @Test
    void laRecuperacionSeAvisaSoloSiVeniaFallando() {
        assertThat(fallas.termino("sw-1")).isFalse();

        fallas.empezo("sw-1");

        assertThat(fallas.termino("sw-1")).isTrue();
        assertThat(fallas.termino("sw-1")).isFalse();
        assertThat(fallas.empezo("sw-1")).isTrue();
    }

    @Test
    void cadaClaveEsIndependiente() {
        fallas.empezo("sw-1");

        assertThat(fallas.empezo("sw-2")).isTrue();
        assertThat(fallas.termino("sw-2")).isTrue();
        assertThat(fallas.empezo("sw-1")).isFalse();
    }
}
