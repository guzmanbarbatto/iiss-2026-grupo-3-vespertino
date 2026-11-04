package com.ioteste.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClientesHttpTest {

    private static final String SIMULADOR_URL = "http://simulador:9090";
    private static final String URL_SWITCH = "http://edificio-b:9090";

    private MockRestServiceServer server;
    private RestClient restClient;
    private EngineClient engineClient;
    private SimuladorClient simuladorClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://srv")
                .defaultHeader("X-API-Key", "clave");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        engineClient = new EngineClient(restClient);
        simuladorClient = new SimuladorClient(restClient, SIMULADOR_URL, "");
    }

    @Test
    void obtenerSitioDevuelveVacioSiElEngineResponde404() {
        server.expect(requestTo("http://srv/sitio")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(engineClient.obtenerSitio()).isEmpty();
        server.verify();
    }

    @Test
    void obtenerSitioDeserializaElInventario() {
        server.expect(requestTo("http://srv/sitio"))
                .andExpect(header("X-API-Key", "clave"))
                .andRespond(withSuccess("""
                        {"schemaVersion":"2.0","sitio":{"id":"s1","nombre":"Casa","potenciaContratadaKW":5.0,
                         "tarifa":{"punta":{"desde":"18:00","hasta":"22:00","dias":"HABILES"}}},
                         "habitaciones":[{"id":"hab-1","nombre":"C1","temperaturaEsperada":20.0,"potenciaKW":2.0,
                         "idTermostato":"ht","topicTermostato":"t","idSwitch":"sw-1","urlSwitch":"u","extra":1}]}
                        """, MediaType.APPLICATION_JSON));

        var inventario = engineClient.obtenerSitio().orElseThrow();

        assertThat(inventario.sitio().potenciaContratadaKW()).isEqualTo(5.0);
        assertThat(inventario.habitaciones()).extracting("idSwitch").containsExactly("sw-1");
    }

    @Test
    void credencialRechazadaDaMensajeAccionable() {
        server.expect(requestTo("http://srv/control")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> engineClient.obtenerControl())
                .isInstanceOf(ServicioExternoException.class)
                .hasMessageContaining("APP_API_KEY");
    }

    @Test
    void detenerControlNoEnviaCamposNulos() {
        server.expect(requestTo("http://srv/control/comandos"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"accion\":\"DETENER\"}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"estado\":\"DETENIDO\"}", MediaType.APPLICATION_JSON));

        assertThat(engineClient.detenerControl().enEjecucion()).isFalse();
        server.verify();
    }

    @Test
    void iniciarControlEnviaFechaYFactor() {
        server.expect(requestTo("http://srv/control/comandos"))
                .andExpect(content().json(
                        "{\"accion\":\"INICIAR\",\"fechaHora\":\"2026-10-01T09:15:00Z\",\"factorTiempo\":10.0}",
                        JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"estado\":\"EN_EJECUCION\"}", MediaType.APPLICATION_JSON));

        assertThat(engineClient.iniciarControl("2026-10-01T09:15:00Z", 10.0).enEjecucion()).isTrue();
        server.verify();
    }

    @Test
    void comandarSwitchEnviaOnAlUrlSwitchDeLaHabitacionYDevuelveLaRespuesta() {
        server.expect(requestTo(URL_SWITCH + "/switches/sw-1/comandos"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "clave"))
                .andExpect(content().json("{\"accion\":\"ON\"}", JsonCompareMode.STRICT))
                .andRespond(withSuccess(
                        "{\"idSwitch\":\"sw-1\",\"estado\":\"ON\",\"cambio\":true,\"aplicadoEn\":\"t\"}",
                        MediaType.APPLICATION_JSON));

        var respuesta = simuladorClient.comandarSwitch(URL_SWITCH + "/", "sw-1", true);

        assertThat(respuesta.cambio()).isTrue();
        assertThat(respuesta.estado()).isEqualTo("ON");
        server.verify();
    }

    @Test
    void consultarSwitchUsaElUrlSwitchDeLaHabitacion() {
        server.expect(requestTo(URL_SWITCH + "/switches/sw-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"idSwitch\":\"sw-1\",\"estado\":\"OFF\"}", MediaType.APPLICATION_JSON));

        assertThat(simuladorClient.consultarSwitch(URL_SWITCH, "sw-1").estado()).isEqualTo("OFF");
        server.verify();
    }

    @Test
    void switchesUrlReemplazaAlUrlSwitchDeLaHabitacion() {
        SimuladorClient conSwitchesUrl = new SimuladorClient(restClient, SIMULADOR_URL, " http://alcanzable:9090 ");
        server.expect(requestTo("http://alcanzable:9090/switches/sw-1"))
                .andRespond(withSuccess("{\"idSwitch\":\"sw-1\",\"estado\":\"ON\"}", MediaType.APPLICATION_JSON));

        assertThat(conSwitchesUrl.consultarSwitch("http://iiss-simulator:9090", "sw-1").estado()).isEqualTo("ON");
        server.verify();
    }

    @Test
    void unaHabitacionSinUrlSwitchUsaSimuladorUrl() {
        server.expect(requestTo(SIMULADOR_URL + "/switches/sw-1"))
                .andRespond(withSuccess("{\"idSwitch\":\"sw-1\",\"estado\":\"ON\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SIMULADOR_URL + "/switches/sw-2"))
                .andRespond(withSuccess("{\"idSwitch\":\"sw-2\",\"estado\":\"OFF\"}", MediaType.APPLICATION_JSON));

        assertThat(simuladorClient.consultarSwitch(null, "sw-1").estado()).isEqualTo("ON");
        assertThat(simuladorClient.consultarSwitch(" ", "sw-2").estado()).isEqualTo("OFF");
        server.verify();
    }

    @Test
    void switchInexistenteDaMensajeClaro() {
        server.expect(requestTo(URL_SWITCH + "/switches/sw-9")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> simuladorClient.consultarSwitch(URL_SWITCH, "sw-9"))
                .isInstanceOf(ServicioExternoException.class)
                .hasMessageContaining("sw-9")
                .hasMessageContaining("no existe en " + URL_SWITCH);
    }

    @Test
    void switchInalcanzableNombraLaDireccionYSugiereSwitchesUrl() {
        server.expect(requestTo("http://iiss-simulator:9090/switches/sw-1/comandos"))
                .andRespond(request -> {
                    throw new IOException("iiss-simulator: nombre desconocido");
                });

        assertThatThrownBy(() -> simuladorClient.comandarSwitch("http://iiss-simulator:9090", "sw-1", false))
                .isInstanceOf(ServicioExternoException.class)
                .hasMessageContaining("http://iiss-simulator:9090")
                .hasMessageContaining("SWITCHES_URL");
    }
}

