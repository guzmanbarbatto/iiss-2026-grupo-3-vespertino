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
import com.ioteste.engine.RelojMutable;
import com.ioteste.engine.service.SwitchClientService.Resultado;
import com.sun.net.httpserver.HttpServer;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestTemplate;

class SwitchClientServiceTest {

    /** El {@code urlSwitch} de las habitaciones en la mayoría de los casos. */
    private static final String SIM = "http://sim";

    private final RelojMutable reloj = new RelojMutable(Instant.parse("2026-11-04T12:00:00Z"));
    private RestTemplate restTemplate;
    private MockRestServiceServer simulador;
    private SwitchClientService cliente;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        simulador = MockRestServiceServer.bindTo(restTemplate).build();
        cliente = cliente("");
    }

    private SwitchClientService cliente(String switchesUrl) {
        return new SwitchClientService(restTemplate, reloj, "http://simulador-registro", switchesUrl, "clave-sim");
    }

    // ---------------------------------------------------------------- contrato (§3 y §4)

    @Test
    void consultaElEstadoConLaClaveDelSimulador() {
        simulador.expect(requestTo("http://sim/switches/sw-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", "clave-sim"))
                .andRespond(json("""
                        {"idSwitch":"sw-1","estado":"ON","actualizadoEn":"2026-10-01T09:15:03.120Z"}"""));

        assertThat(cliente.consultarEstado(SIM, "sw-1")).isEqualTo(EstadoSwitch.ON);
        simulador.verify();
    }

    @Test
    void interpretaElCampoEstadoYNoElTextoDeLaRespuesta() {
        // Antes se buscaba "ON" en todo el cuerpo: este switch se leía como encendido
        simulador.expect(requestTo("http://sim/switches/sw-SALON"))
                .andRespond(json("{\"idSwitch\":\"sw-SALON\",\"estado\":\"OFF\",\"extra\":1}"));

        assertThat(cliente.consultarEstado(SIM, "sw-SALON")).isEqualTo(EstadoSwitch.OFF);
    }

    @Test
    void desconocidoNoSeAsumeApagado() {
        simulador.expect(requestTo("http://sim/switches/sw-1"))
                .andRespond(json("{\"idSwitch\":\"sw-1\",\"estado\":\"DESCONOCIDO\",\"actualizadoEn\":null}"));

        assertThat(cliente.consultarEstado(SIM, "sw-1")).isEqualTo(EstadoSwitch.DESCONOCIDO);
    }

    @Test
    void siLaConsultaFallaElEstadoEsDesconocido() {
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        simulador.expect(requestTo("http://sim/switches/sw-2")).andRespond(withException(new ConnectException("caído")));
        simulador.expect(requestTo("http://sim/switches/sw-3")).andRespond(withStatus(HttpStatus.OK));

        assertThat(cliente.consultarEstado(SIM, "sw-1")).isEqualTo(EstadoSwitch.DESCONOCIDO);
        assertThat(cliente.consultarEstado(SIM, "sw-2")).isEqualTo(EstadoSwitch.DESCONOCIDO);
        assertThat(cliente.consultarEstado(SIM, "sw-3")).isEqualTo(EstadoSwitch.DESCONOCIDO);
    }

    @Test
    void comandaConElCuerpoDelContrato() {
        simulador.expect(requestTo("http://sim/switches/sw-1/comandos"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "clave-sim"))
                .andExpect(content().json("{\"accion\":\"OFF\"}", JsonCompareMode.STRICT))
                .andRespond(json("{\"idSwitch\":\"sw-1\",\"estado\":\"OFF\",\"cambio\":true,\"aplicadoEn\":\"t\"}"));

        assertThat(cliente.comandarSwitch(SIM, "sw-1", "OFF")).isEqualTo(Resultado.APLICADO);
        simulador.verify();
    }

    @Test
    void distingueUnSwitchInexistenteDeUnaFallaSinLanzar() {
        simulador.expect(requestTo("http://sim/switches/sw-9/comandos")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        simulador.expect(requestTo("http://sim/switches/sw-1/comandos"))
                .andRespond(withException(new ConnectException("caído")));

        assertThat(cliente.comandarSwitch(SIM, "sw-9", "ON")).isEqualTo(Resultado.INEXISTENTE);
        assertThat(cliente.comandarSwitch(SIM, "sw-1", "ON")).isEqualTo(Resultado.FALLIDO);
    }

    @Test
    void unSwitchQueNoContestaSeDaPorFalladoAlSegundoSinEsperarMas() throws Exception {
        // Acepta la conexión y no contesta (la falla F05 de ecowarm-run): cada llamada espera el timeout y no más
        ExecutorService hilos = Executors.newCachedThreadPool();
        HttpServer colgado = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        colgado.createContext("/", intercambio -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        colgado.setExecutor(hilos);
        colgado.start();
        try {
            SwitchClientService real = new SwitchClientService(reloj, "http://simulador-registro", "", "clave-sim");
            String url = "http://127.0.0.1:" + colgado.getAddress().getPort();
            long inicio = System.nanoTime();

            assertThat(real.consultarEstado(url, "sw-1")).isEqualTo(EstadoSwitch.DESCONOCIDO);
            assertThat(real.comandarSwitch(url, "sw-1", "OFF")).isEqualTo(Resultado.FALLIDO);

            Duration espera = Duration.ofNanos(System.nanoTime() - inicio);
            assertThat(SwitchClientService.READ_TIMEOUT_MS).isEqualTo(1000);
            assertThat(espera).isBetween(Duration.ofMillis(1900), Duration.ofMillis(3500));
            // Para conectar se espera más: en Wi-Fi, un paquete de conexión perdido se reintenta recién al segundo
            assertThat(SwitchClientService.CONNECT_TIMEOUT_MS).isEqualTo(3000);
        } finally {
            colgado.stop(0);
            hilos.shutdownNow();
        }
    }

    // ---------------------------------------------------------------- dónde está cada switch

    @Test
    void cadaSwitchSeUsaEnElUrlSwitchDeSuHabitacion() {
        simulador.expect(requestTo("http://switches-a:8081/switches/sw-1")).andRespond(json("{\"estado\":\"OFF\"}"));
        simulador.expect(requestTo("http://switches-b:8081/switches/sw-2/comandos")).andRespond(withSuccess());

        cliente.consultarEstado("http://switches-a:8081", "sw-1");
        cliente.comandarSwitch("http://switches-b:8081/", "sw-2", "ON"); // la barra final no se duplica

        simulador.verify();
    }

    @Test
    void switchesUrlReemplazaAlUrlSwitchCuandoElNombreAnunciadoNoSeAlcanza() {
        SwitchClientService conReemplazo = cliente("http://host.docker.internal:9090");
        simulador.expect(requestTo("http://host.docker.internal:9090/switches/sw-1/comandos"))
                .andRespond(withSuccess());

        conReemplazo.comandarSwitch("http://iiss-simulator:9090", "sw-1", "ON");

        simulador.verify();
    }

    @Test
    void sinUrlSwitchSeUsaElSimulador() {
        simulador.expect(requestTo("http://simulador-registro/switches/sw-1/comandos")).andRespond(withSuccess());

        assertThat(cliente.comandarSwitch(null, "sw-1", "OFF")).isEqualTo(Resultado.APLICADO);
        simulador.verify();
    }

    // ---------------------------------------------------------------- estado conocido: menos consultas

    @Test
    void elEstadoConocidoSeUsaSinConsultarHastaQueVence() {
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(json("{\"estado\":\"ON\"}"));
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(json("{\"estado\":\"OFF\"}"));

        assertThat(cliente.consultarEstado(SIM, "sw-1")).isEqualTo(EstadoSwitch.ON);
        reloj.avanzar(SwitchClientService.ESTADO_VIGENTE.minusMillis(1));
        assertThat(cliente.consultarEstado(SIM, "sw-1")).isEqualTo(EstadoSwitch.ON); // sin consultar
        reloj.avanzar(Duration.ofMillis(1));
        assertThat(cliente.consultarEstado(SIM, "sw-1")).isEqualTo(EstadoSwitch.OFF); // venció: consulta

        simulador.verify();
    }

    @Test
    void unComandoAplicadoDejaElEstadoConocidoSinConsultar() {
        simulador.expect(requestTo("http://sim/switches/sw-1/comandos")).andRespond(withSuccess());

        cliente.comandarSwitch(SIM, "sw-1", "ON");

        assertThat(cliente.consultarEstado(SIM, "sw-1")).isEqualTo(EstadoSwitch.ON);
        simulador.verify(); // una consulta de más haría fallar al simulador falso
    }

    @Test
    void unaFallaAlComandarObligaAVolverAConsultar() {
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(json("{\"estado\":\"ON\"}"));
        simulador.expect(requestTo("http://sim/switches/sw-1/comandos"))
                .andRespond(withException(new ConnectException("caído")));
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(json("{\"estado\":\"OFF\"}"));

        cliente.consultarEstado(SIM, "sw-1");
        cliente.comandarSwitch(SIM, "sw-1", "OFF");

        assertThat(cliente.consultarEstado(SIM, "sw-1")).isEqualTo(EstadoSwitch.OFF);
        simulador.verify();
    }

    @Test
    void siLaConsultaFallaUnSwitchQueEstabaEncendidoSigueContandoComoEncendido() {
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(json("{\"estado\":\"ON\"}"));
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(withException(new ConnectException("x")));
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(json("{\"estado\":\"on\"}"));
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(json("{\"estado\":\"OFF\"}"));

        assertThat(cliente.consultarEstado(SIM, "sw-1")).isEqualTo(EstadoSwitch.ON);
        reloj.avanzar(SwitchClientService.ESTADO_VIGENTE);
        // Un Connect timed out no apaga la calefacción: no se la deja como desconocida para que no se rote
        assertThat(cliente.consultarEstado(SIM, "sw-1")).isEqualTo(EstadoSwitch.ON);
        // Ni una respuesta que no se entiende (F07: el estado en minúsculas); y se sigue consultando en cada ciclo
        assertThat(cliente.consultarEstado(SIM, "sw-1")).isEqualTo(EstadoSwitch.ON);
        assertThat(cliente.consultarEstado(SIM, "sw-1")).isEqualTo(EstadoSwitch.OFF);
        simulador.verify();
    }

    @Test
    void siLaConsultaFallaUnSwitchQueEstabaApagadoEsDesconocido() {
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(json("{\"estado\":\"OFF\"}"));
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(withException(new ConnectException("x")));

        cliente.consultarEstado(SIM, "sw-1");
        reloj.avanzar(SwitchClientService.ESTADO_VIGENTE);

        // Podría haberse encendido: no se supone apagado
        assertThat(cliente.consultarEstado(SIM, "sw-1")).isEqualTo(EstadoSwitch.DESCONOCIDO);
        simulador.verify();
    }

    @Test
    void unApagadoQueNoSeConfirmaNoDejaElEncendidoComoConocido() {
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(json("{\"estado\":\"ON\"}"));
        simulador.expect(requestTo("http://sim/switches/sw-1/comandos"))
                .andRespond(withException(new ConnectException("x")));
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(withException(new ConnectException("x")));

        cliente.consultarEstado(SIM, "sw-1");
        cliente.comandarSwitch(SIM, "sw-1", "OFF");

        // F05: no se sabe si el OFF llegó. Desconocido (la potencia sigue contando), no "encendido conocido"
        assertThat(cliente.consultarEstado(SIM, "sw-1")).isEqualTo(EstadoSwitch.DESCONOCIDO);
        simulador.verify();
    }

    @Test
    void unEstadoDesconocidoNoSeRecuerda() {
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(json("{\"estado\":\"DESCONOCIDO\"}"));
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(json("{\"estado\":\"OFF\"}"));

        cliente.consultarEstado(SIM, "sw-1");

        assertThat(cliente.consultarEstado(SIM, "sw-1")).isEqualTo(EstadoSwitch.OFF);
        simulador.verify();
    }

    @Test
    void olvidarEstadosObligaAVolverAConsultar() {
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(json("{\"estado\":\"ON\"}"));
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(json("{\"estado\":\"OFF\"}"));

        cliente.consultarEstado(SIM, "sw-1");
        cliente.olvidarEstados();

        assertThat(cliente.consultarEstado(SIM, "sw-1")).isEqualTo(EstadoSwitch.OFF);
        simulador.verify();
    }

    // ---------------------------------------------------------------- avisos

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void unSwitchCaidoSeAvisaUnaVezYOtraCuandoVuelveAResponder(CapturedOutput salida) {
        for (int i = 0; i < 3; i++) {
            simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(withException(new ConnectException("x")));
        }
        simulador.expect(requestTo("http://sim/switches/sw-1/comandos"))
                .andRespond(withException(new ConnectException("x")));
        simulador.expect(requestTo("http://sim/switches/sw-1")).andRespond(json("{\"estado\":\"OFF\"}"));

        for (int i = 0; i < 3; i++) {
            cliente.consultarEstado(SIM, "sw-1");
        }
        cliente.comandarSwitch(SIM, "sw-1", "ON");
        cliente.consultarEstado(SIM, "sw-1");

        assertThat(salida.getOut().split("No se pudo", -1)).hasSize(2);
        assertThat(salida.getOut().split("El switch sw-1 vuelve a responder", -1)).hasSize(2);
    }

    private static ResponseCreator json(String cuerpo) {
        return withSuccess(cuerpo, MediaType.APPLICATION_JSON);
    }
}
