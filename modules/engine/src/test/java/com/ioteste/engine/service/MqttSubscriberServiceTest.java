package com.ioteste.engine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ioteste.engine.RelojMutable;
import com.ioteste.engine.domain.Franja;
import com.ioteste.engine.domain.Habitacion;
import com.ioteste.engine.domain.InventarioDTO;
import com.ioteste.engine.domain.InventarioReemplazado;
import com.ioteste.engine.domain.Sitio;
import com.ioteste.engine.domain.Tarifa;
import com.ioteste.engine.repository.HistoricoRepository;
import com.ioteste.engine.repository.SitioRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class MqttSubscriberServiceTest {

    private static final String TOPIC = "ht-room1/status/temperature:0";
    /** Un topic fuera de la convención: con el comodín de antes, sus lecturas nunca llegaban. */
    private static final String TOPIC_NO_CONVENCIONAL = "casa/estudio/temperatura";
    private static final Instant AHORA = Instant.parse("2026-11-03T15:00:00Z");

    @Mock private HistoricoRepository historico;
    @Mock private SitioRepository sitioRepository;
    @Mock private IMqttAsyncClient cliente;

    private MqttSubscriberService servicio;

    @BeforeEach
    void setUp() {
        servicio = new MqttSubscriberService(historico, sitioRepository, new ObjectMapper(),
                new RelojMutable(AHORA), "tcp://broker:1883");
        lenient().when(cliente.isConnected()).thenReturn(true);
    }

    // ---------------------------------------------------------------- suscripción a los topics declarados

    @Test
    void alConectarseSeSuscribeExactamenteALosTopicsDelInventario() throws Exception {
        when(sitioRepository.obtenerInventario()).thenReturn(Optional.of(inventario(TOPIC, TOPIC_NO_CONVENCIONAL)));

        conectar(false);

        assertThat(suscripciones(1)).containsExactly(List.of(TOPIC, TOPIC_NO_CONVENCIONAL));
    }

    @Test
    void despuesDeUnaReconexionVuelveASuscribirseATodo() throws Exception {
        // Con cleanSession=true el broker olvida las suscripciones al cortarse la conexión
        when(sitioRepository.obtenerInventario()).thenReturn(Optional.of(inventario(TOPIC)));

        conectar(false);
        conectar(true);

        assertThat(suscripciones(2)).containsExactly(List.of(TOPIC), List.of(TOPIC));
    }

    @Test
    void sinSitioCargadoNoSeSuscribeHastaQueLlegaElPut() throws Exception {
        when(sitioRepository.obtenerInventario()).thenReturn(Optional.empty());

        conectar(false);
        verify(cliente, never()).subscribe(any(String[].class), any(int[].class), any(), any());

        servicio.inventarioReemplazado(new InventarioReemplazado(Optional.empty(), inventario(TOPIC)));

        assertThat(suscripciones(1)).containsExactly(List.of(TOPIC));
    }

    @Test
    void unPutSeSuscribeALosTopicsNuevosYDejaLosQueSalieron() throws Exception {
        String topic2 = "ht-room2/status/temperature:0";
        String topic3 = "ht-room3/status/temperature:1";
        when(sitioRepository.obtenerInventario()).thenReturn(Optional.of(inventario(TOPIC, topic2)));
        conectar(false);

        servicio.inventarioReemplazado(new InventarioReemplazado(Optional.empty(), inventario(topic2, topic3)));

        ArgumentCaptor<String[]> desuscriptos = ArgumentCaptor.forClass(String[].class);
        verify(cliente).unsubscribe(desuscriptos.capture(), isNull(), any(IMqttActionListener.class));
        assertThat(desuscriptos.getValue()).containsExactly(TOPIC);
        assertThat(suscripciones(2)).containsExactly(List.of(TOPIC, topic2), List.of(topic3));
    }

    @Test
    void unPutAntesDeConectarseQuedaParaCuandoSeConecte() throws Exception {
        servicio.inventarioReemplazado(new InventarioReemplazado(Optional.empty(), inventario(TOPIC)));
        verifyNoInteractions(cliente);

        when(sitioRepository.obtenerInventario()).thenReturn(Optional.of(inventario(TOPIC)));
        conectar(false);

        assertThat(suscripciones(1)).containsExactly(List.of(TOPIC));
    }

    @Test
    void siLaBaseNoRespondeAlReconectarseUsaLosUltimosTopicsConocidos() throws Exception {
        when(sitioRepository.obtenerInventario())
                .thenReturn(Optional.of(inventario(TOPIC)))
                .thenThrow(new DataAccessResourceFailureException("base caída"));

        conectar(false);
        conectar(true);

        assertThat(suscripciones(2)).containsExactly(List.of(TOPIC), List.of(TOPIC));
    }

    // ---------------------------------------------------------------- lecturas

    @Test
    void guardaLaLecturaConSuTimestampEIgnoraCamposExtra() {
        procesar(TOPIC, "{\"id\":0,\"tC\":21.5,\"tF\":70.7,\"ts\":1759310103.12,\"errors\":[]}");

        verify(historico).registrar(TOPIC, Instant.ofEpochMilli(1_759_310_103_120L), AHORA, 21.5);
    }

    @Test
    void sinTimestampUsaLaHoraDeRecepcion() {
        procesar(TOPIC, "{\"id\":0,\"tC\":18.0}");

        verify(historico).registrar(TOPIC, AHORA, AHORA, 18.0);
    }

    @Test
    void descartaMensajesSinTemperaturaOInvalidos() {
        procesar(TOPIC, "{\"id\":0,\"tF\":70.7}");
        procesar(TOPIC, "esto no es JSON");

        verify(historico, never()).registrar(anyString(), any(), any(), anyDouble());
    }

    @Test
    void descartaLosMensajesFueraDeContratoDelEscenarioF04() {
        // Las fallas de termostato del simulador de la cátedra (ecowarm-run): ninguna es una lectura
        procesar(TOPIC, "ERROR");                                // texto: no es JSON
        procesar(TOPIC, "{\"id\":0,\"tF\":70.7,\"ts\":1.0}");    // sinTC
        procesar(TOPIC, "{\"id\":0,\"tC\":\"20.0\",\"ts\":1.0}"); // tcTexto: Jackson lo convertía a 20.0
        procesar(TOPIC, "{\"id\":0,\"tC\":-40.0,\"ts\":1.0}");   // fueraDeRango: el valor de error de un sensor
        procesar(TOPIC, "{\"id\":0,\"tC\":85.0}");               // otro valor de error típico
        procesar(TOPIC, "{\"id\":0,\"tC\":null}");
        procesar(TOPIC, "[21.5]");                               // JSON, pero no un objeto
        procesar(TOPIC, "");

        verify(historico, never()).registrar(anyString(), any(), any(), anyDouble());
    }

    @Test
    void losBordesDelRangoPlausibleSonLecturasValidas() {
        procesar(TOPIC, "{\"tC\":" + MqttSubscriberService.TEMPERATURA_MINIMA_C + "}");
        procesar(TOPIC, "{\"tC\":" + MqttSubscriberService.TEMPERATURA_MAXIMA_C + "}");

        verify(historico).registrar(TOPIC, AHORA, AHORA, -10.0);
        verify(historico).registrar(TOPIC, AHORA, AHORA, 50.0);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void unTermostatoQueMandaFueraDeContratoSeAvisaUnaVezYOtraAlRecuperarse(CapturedOutput salida) {
        for (int i = 0; i < 5; i++) {
            procesar(TOPIC, "{\"id\":0,\"tC\":-40.0}");
        }
        procesar(TOPIC, "{\"id\":0,\"tC\":20.1}");
        procesar(TOPIC, "{\"id\":0,\"tC\":20.2}");

        assertThat(salida.getOut().split("manda mensajes fuera del contrato", -1)).hasSize(2);
        assertThat(salida.getOut())
                .contains("ht-room1 manda mensajes fuera del contrato (tC fuera de rango: -40.0 °C)");
        assertThat(salida.getOut().split("ht-room1 vuelve a mandar lecturas válidas", -1)).hasSize(2);
        verify(historico).registrar(TOPIC, AHORA, AHORA, 20.1);
    }

    @Test
    void unaFallaDeBaseDeDatosNoPropagaLaExcepcion() {
        when(historico.registrar(anyString(), any(), any(), anyDouble()))
                .thenThrow(new DataAccessResourceFailureException("caída"));

        assertThatCode(() -> procesar(TOPIC, "{\"tC\":20.0}")).doesNotThrowAnyException();
    }

    @Test
    void losMensajesDeCualquierTopicDeclaradoSeRegistran() {
        servicio.new Suscripcion(cliente).messageArrived(TOPIC_NO_CONVENCIONAL,
                new MqttMessage("{\"tC\":19.5}".getBytes(StandardCharsets.UTF_8)));

        verify(historico).registrar(TOPIC_NO_CONVENCIONAL, AHORA, AHORA, 19.5);
    }

    @Test
    void elLogMuestraElTermostatoSiElTopicSigueLaConvencionYSiNoElTopicCompleto() {
        assertThat(MqttSubscriberService.termostato(TOPIC)).isEqualTo("ht-room1");
        assertThat(MqttSubscriberService.termostato(TOPIC_NO_CONVENCIONAL)).isEqualTo(TOPIC_NO_CONVENCIONAL);
    }

    // ---------------------------------------------------------------- auxiliares

    private void conectar(boolean reconexion) {
        servicio.new Suscripcion(cliente).connectComplete(reconexion, "tcp://broker:1883");
    }

    /** Los topics de cada llamada a subscribe, en orden; verifica también QoS 1 para todos. */
    private List<List<String>> suscripciones(int veces) throws Exception {
        ArgumentCaptor<String[]> topics = ArgumentCaptor.forClass(String[].class);
        ArgumentCaptor<int[]> qos = ArgumentCaptor.forClass(int[].class);
        verify(cliente, times(veces)).subscribe(topics.capture(), qos.capture(), isNull(),
                any(IMqttActionListener.class));
        qos.getAllValues().forEach(q -> assertThat(q).containsOnly(1));
        return topics.getAllValues().stream().map(List::of).toList();
    }

    private void procesar(String topic, String payload) {
        servicio.procesar(topic, payload.getBytes(StandardCharsets.UTF_8));
    }

    private static InventarioDTO inventario(String... topics) {
        List<Habitacion> habitaciones = IntStream.range(0, topics.length)
                .mapToObj(i -> new Habitacion("room" + i, "Cuarto", 20.0, 1.0, "ht-" + i, topics[i], "sw-" + i,
                        "http://sim:9090"))
                .toList();
        return new InventarioDTO("2.0",
                new Sitio("casa", "Casa", 3.7, new Tarifa(new Franja("17:00", "23:00", "HABILES"))), habitaciones);
    }
}
