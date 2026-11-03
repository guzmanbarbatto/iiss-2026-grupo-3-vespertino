package com.ioteste.engine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ioteste.engine.RelojMutable;
import com.ioteste.engine.repository.HistoricoRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class MqttSubscriberServiceTest {

    private static final String TOPIC = "ht-room1/status/temperature:0";
    private static final Instant AHORA = Instant.parse("2026-11-03T15:00:00Z");

    @Mock private HistoricoRepository historico;
    @Mock private IMqttAsyncClient cliente;

    private MqttSubscriberService servicio;

    @BeforeEach
    void setUp() {
        servicio = new MqttSubscriberService(historico, new ObjectMapper(), new RelojMutable(AHORA),
                "tcp://broker:1883");
    }

    @Test
    void guardaLaLecturaConSuTimestampEIgnoraCamposExtra() {
        procesar("{\"id\":0,\"tC\":21.5,\"tF\":70.7,\"ts\":1759310103.12,\"errors\":[]}");

        verify(historico).registrar(TOPIC, Instant.ofEpochMilli(1_759_310_103_120L), 21.5);
    }

    @Test
    void sinTimestampUsaLaHoraDeRecepcion() {
        procesar("{\"id\":0,\"tC\":18.0}");

        verify(historico).registrar(TOPIC, AHORA, 18.0);
    }

    @Test
    void descartaMensajesSinTemperaturaOInvalidos() {
        procesar("{\"id\":0,\"tF\":70.7}");
        procesar("esto no es JSON");

        verify(historico, never()).registrar(anyString(), any(), anyDouble());
    }

    @Test
    void unaFallaDeBaseDeDatosNoPropagaLaExcepcion() {
        when(historico.registrar(anyString(), any(), anyDouble()))
                .thenThrow(new DataAccessResourceFailureException("caída"));

        assertThatCode(() -> procesar("{\"tC\":20.0}")).doesNotThrowAnyException();
    }

    @Test
    void seSuscribeEnLaConexionInicial() throws Exception {
        servicio.new Suscripcion(cliente).connectComplete(false, "tcp://broker:1883");

        verify(cliente).subscribe(MqttSubscriberService.TOPICO_TEMPERATURAS, 1);
    }

    @Test
    void vuelveASuscribirseDespuesDeUnaReconexionAutomatica() throws Exception {
        // Con cleanSession=true el broker olvida la suscripción: sin esto dejaban de llegar temperaturas
        servicio.new Suscripcion(cliente).connectComplete(true, "tcp://broker:1883");

        verify(cliente).subscribe(MqttSubscriberService.TOPICO_TEMPERATURAS, 1);
    }

    @Test
    void losMensajesQueLleganSeRegistran() {
        servicio.new Suscripcion(cliente).messageArrived(TOPIC, new MqttMessage("{\"tC\":19.5}".getBytes()));

        verify(historico).registrar(TOPIC, AHORA, 19.5);
    }

    @Test
    void elLogMuestraSoloElIdDelTermostato() {
        assertThat(MqttSubscriberService.termostato(TOPIC)).isEqualTo("ht-room1");
        assertThat(MqttSubscriberService.termostato("sin-barra")).isEqualTo("sin-barra");
    }

    private void procesar(String payload) {
        servicio.procesar(TOPIC, payload.getBytes(StandardCharsets.UTF_8));
    }
}
