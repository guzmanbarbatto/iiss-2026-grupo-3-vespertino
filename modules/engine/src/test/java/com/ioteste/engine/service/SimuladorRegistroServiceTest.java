package com.ioteste.engine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ioteste.engine.RelojMutable;
import com.ioteste.engine.domain.ControladorRegistrado.BrokerMqtt;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class SimuladorRegistroServiceTest {

    private static final String CONTROLADORES = "http://sim/controladores";

    @Mock private ApplicationEventPublisher eventos;
    @Mock private TaskScheduler scheduler;

    private final RelojMutable clock = new RelojMutable(Instant.parse("2026-11-03T15:00:00Z"));
    private MockRestServiceServer simulador;
    private SimuladorRegistroService registro;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        simulador = MockRestServiceServer.bindTo(restTemplate).build();
        registro = new SimuladorRegistroService(restTemplate, eventos, scheduler, clock,
                "http://sim", "clave-sim", "mi-clave", "http://engine:8080");
    }

    @Test
    void alArrancarProgramaElMantenimientoDelRegistro() {
        registro.iniciar();

        verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(SimuladorRegistroService.REINTENTO));
    }

    @Test
    void seRegistraYPublicaElBrokerQueAnunciaElSimulador() {
        esperarRegistro();

        registro.mantenerRegistro();

        simulador.verify();
        verify(eventos).publishEvent(new BrokerMqtt("mosquitto", 1883));
    }

    @Test
    void reintentaHastaQueElSimuladorResponde() {
        simulador.expect(requestTo(CONTROLADORES)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        esperarRegistro();

        registro.mantenerRegistro();
        verify(eventos, never()).publishEvent(any(Object.class));
        registro.mantenerRegistro();

        simulador.verify();
        verify(eventos).publishEvent(new BrokerMqtt("mosquitto", 1883));
    }

    @Test
    void noVuelveALlamarAntesDeLaProximaVerificacion() {
        esperarRegistro();
        registro.mantenerRegistro();

        clock.avanzar(Duration.ofSeconds(30));
        registro.mantenerRegistro();

        simulador.verify(); // una sola llamada: MockRestServiceServer falla ante pedidos no esperados
    }

    @Test
    void siElSimuladorLoSigueConociendoNoSeVuelveARegistrar() {
        esperarRegistro();
        simulador.expect(requestTo(CONTROLADORES)).andExpect(method(HttpMethod.GET))
                .andRespond(json("[{\"id\":\"otro\",\"etiqueta\":\"otro\"},"
                        + "{\"id\":\"control-grupo-3\",\"etiqueta\":\"control-grupo-3\"}]"));

        registro.mantenerRegistro();
        clock.avanzar(SimuladorRegistroService.VERIFICACION);
        registro.mantenerRegistro();

        simulador.verify(); // registro + verificación, sin un segundo POST
        verify(eventos).publishEvent(any(Object.class));
    }

    @Test
    void siElSimuladorSeReinicioYLoOlvidoSeVuelveARegistrar() {
        esperarRegistro();
        simulador.expect(requestTo(CONTROLADORES)).andExpect(method(HttpMethod.GET)).andRespond(json("[]"));
        esperarRegistro();

        registro.mantenerRegistro();
        clock.avanzar(SimuladorRegistroService.VERIFICACION);
        registro.mantenerRegistro();

        simulador.verify();
    }

    @Test
    void siElSimuladorNoOfreceElListadoSeAsumeRegistrado() {
        esperarRegistro();
        simulador.expect(requestTo(CONTROLADORES)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        registro.mantenerRegistro();
        clock.avanzar(SimuladorRegistroService.VERIFICACION);
        registro.mantenerRegistro();

        simulador.verify();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void mientrasElSimuladorNoRespondeSeAvisaUnaSolaVez(CapturedOutput salida) {
        for (int i = 0; i < 3; i++) {
            simulador.expect(requestTo(CONTROLADORES)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }
        esperarRegistro();

        for (int i = 0; i < 4; i++) {
            registro.mantenerRegistro();
        }

        simulador.verify();
        assertThat(salida.getOut().split("El simulador no responde", -1)).hasSize(2);
        assertThat(salida.getOut()).contains("El simulador vuelve a responder");
    }

    private void esperarRegistro() {
        simulador.expect(requestTo(CONTROLADORES))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "clave-sim"))
                .andExpect(content().json("""
                        {"etiqueta":"control-grupo-3","baseUrl":"http://engine:8080","apiKey":"mi-clave"}
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("""
                        {"id":"control-grupo-3","etiqueta":"control-grupo-3","baseUrl":"http://engine:8080",
                         "registradoEn":"2026-10-01T09:14:58.000Z","mqtt":{"host":"mosquitto","puerto":1883}}
                        """));
    }

    private static ResponseCreator json(String cuerpo) {
        return withSuccess(cuerpo, MediaType.APPLICATION_JSON);
    }
}
