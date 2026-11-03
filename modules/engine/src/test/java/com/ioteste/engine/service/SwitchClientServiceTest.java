package com.ioteste.engine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ioteste.core.domain.EstadoSwitch;
import java.net.ConnectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestTemplate;

class SwitchClientServiceTest {

    private MockRestServiceServer simulador;
    private SwitchClientService cliente;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        simulador = MockRestServiceServer.bindTo(restTemplate).build();
        cliente = new SwitchClientService(restTemplate, "http://sim", "clave-sim");
    }

    @Test
    void consultaElEstadoConLaClaveDelSimulador() {
        simulador.expect(requestTo("http://sim/switches/sw-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", "clave-sim"))
                .andRespond(json("""
                        {"idSwitch":"sw-1","estado":"ON","actualizadoEn":"2026-10-01T09:15:03.120Z"}"""));

        assertThat(cliente.consultarEstado("sw-1")).isEqualTo(EstadoSwitch.ON);
        simulador.verify();
    }

    @Test
    void interpretaElCampoEstadoYNoElTextoDeLaRespuesta() {
        // Antes se buscaba "ON" en todo el cuerpo: este switch se leía como encendido
        simulador.expect(requestTo("http://sim/switches/sw-SALON"))
                .andRespond(json("{\"idSwitch\":\"sw-SALON\",\"estado\":\"OFF\",\"extra\":1}"));

        assertThat(cliente.consultarEstado("sw-SALON")).isEqualTo(EstadoSwitch.OFF);
    }

    @Test
    void desconocidoNoSeAsumeApagado() {
        simulador.expect(requestTo("http://sim/switches/sw-1"))
                .andRespond(json("{\"idSwitch\":\"sw-1\",\"estado\":\"DESCONOCIDO\",\"actualizadoEn\":null}"));

        assertThat(cliente.consultarEstado("sw-1")).isEqualTo(EstadoSwitch.DESCONOCIDO);
    }

    @Test
    void siLaConsultaFallaElEstadoEsDesconocido() {
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        simulador.expect(requestTo("http://sim/switches/sw-2")).andRespond(withException(new ConnectException("caído")));
        simulador.expect(requestTo("http://sim/switches/sw-3")).andRespond(withStatus(HttpStatus.OK));

        assertThat(cliente.consultarEstado("sw-1")).isEqualTo(EstadoSwitch.DESCONOCIDO);
        assertThat(cliente.consultarEstado("sw-2")).isEqualTo(EstadoSwitch.DESCONOCIDO);
        assertThat(cliente.consultarEstado("sw-3")).isEqualTo(EstadoSwitch.DESCONOCIDO);
    }

    @Test
    void comandaConElCuerpoDelContrato() {
        simulador.expect(requestTo("http://sim/switches/sw-1/comandos"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "clave-sim"))
                .andExpect(content().json("{\"accion\":\"OFF\"}", JsonCompareMode.STRICT))
                .andRespond(json("{\"idSwitch\":\"sw-1\",\"estado\":\"OFF\",\"cambio\":true,\"aplicadoEn\":\"t\"}"));

        assertThat(cliente.comandarSwitch("sw-1", "OFF")).isTrue();
        simulador.verify();
    }

    @Test
    void unComandoFallidoDevuelveFalseSinLanzar() {
        simulador.expect(requestTo("http://sim/switches/sw-9/comandos")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        simulador.expect(requestTo("http://sim/switches/sw-1/comandos"))
                .andRespond(withException(new ConnectException("caído")));

        assertThat(cliente.comandarSwitch("sw-9", "ON")).isFalse();
        assertThat(cliente.comandarSwitch("sw-1", "ON")).isFalse();
    }

    private static ResponseCreator json(String cuerpo) {
        return withSuccess(cuerpo, MediaType.APPLICATION_JSON);
    }
}
