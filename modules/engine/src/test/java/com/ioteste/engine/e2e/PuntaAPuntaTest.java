package com.ioteste.engine.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ioteste.engine.domain.Franja;
import com.ioteste.engine.domain.Habitacion;
import com.ioteste.engine.domain.InventarioDTO;
import com.ioteste.engine.domain.Sitio;
import com.ioteste.engine.domain.Tarifa;
import com.ioteste.engine.e2e.SimuladorFalso.Comando;
import com.ioteste.engine.repository.HistoricoRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * De punta a punta: el engine completo (API REST, registro, MQTT, ciclo de control y base) contra un PostgreSQL
 * y un Mosquitto reales. Un {@link SimuladorFalso} hace de switches y anuncia el broker; los {@link Termostatos}
 * publican por MQTT. El test actúa como el simulador de la cátedra (carga el sitio y arranca el control) y
 * verifica lo que se ve desde afuera: qué comandos reciben los switches y cuándo.
 *
 * <p>Corre a ×600 (un ciclo cada 0.5 s reales, 10 minutos simulados por segundo) un sábado, para que la
 * tarifa punta (17 a 23, días hábiles) no interfiera salvo en su propio escenario. Sin Docker se omite.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PuntaAPuntaTest {

    private static final String CLAVE_ENGINE = "clave-engine-e2e";
    private static final String CLAVE_SIMULADOR = "clave-simulador-e2e";
    private static final double POTENCIA_CONTRATADA = 3.0;
    private static final double ESPERADA = 21.0;
    private static final double TIBIA = 25.0;
    private static final double FRIA = 15.0;
    private static final String SABADO_9HS = "2026-10-31T09:00:00-03:00";
    private static final double X600 = 600;
    private static final Duration UN_PAR_DE_CICLOS = Duration.ofSeconds(5);

    /**
     * Una habitación del sitio de prueba. Su {@code urlSwitch} es el simulador falso más {@code ruta}: room3 tiene
     * el switch "en otro servidor" ({@code /edificio-b}) y además publica en un topic fuera de la convención.
     */
    private record Hab(String id, String topic, String idSwitch, double potenciaKW, String ruta) { }

    private static final Hab ROOM1 = new Hab("room1", "ht-room1/status/temperature:0", "sw-room1", 1.5, "");
    private static final Hab ROOM2 = new Hab("room2", "ht-room2/status/temperature:0", "sw-room2", 1.0, "");
    private static final Hab ROOM3 = new Hab("room3", "casa/estudio/temperatura", "sw-room3", 1.0, "/edificio-b");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static final GenericContainer<?> MOSQUITTO = new GenericContainer<>("eclipse-mosquitto:2")
            .withCopyToContainer(Transferable.of("listener 1883\nallow_anonymous true\n"),
                    "/mosquitto/config/mosquitto.conf")
            .withExposedPorts(1883);

    static {
        // Puertos del host fijos: así un reinicio del contenedor no los cambia y el engine puede reconectarse
        fijarPuerto(POSTGRES, 5432);
        fijarPuerto(MOSQUITTO, 1883);
    }

    private static SimuladorFalso simulador;
    private static Termostatos termostatos;

    @Autowired private TestRestTemplate http;
    @Autowired private HistoricoRepository historico;

    @DynamicPropertySource
    static void configurarEngine(DynamicPropertyRegistry propiedades) {
        propiedades.add("app.api.key", () -> CLAVE_ENGINE);
        propiedades.add("simulador.url", () -> simulador.url());
        propiedades.add("simulador.api.key", () -> CLAVE_SIMULADOR);
        // Vacío: el engine usa el broker que le anuncia el simulador al registrarse (§6)
        propiedades.add("mqtt.broker.url", () -> "");
        // Una línea por lectura (6 por segundo) taparía el resto del log
        propiedades.add("logging.level.com.ioteste.engine.service.MqttSubscriberService", () -> "WARN");
    }

    @BeforeAll
    static void levantarSimuladorYTermostatos() throws Exception {
        Map<String, Double> potencias = new HashMap<>();
        List.of(ROOM1, ROOM2, ROOM3).forEach(h -> potencias.put(h.idSwitch(), h.potenciaKW()));
        simulador = new SimuladorFalso(CLAVE_SIMULADOR, potencias, MOSQUITTO.getHost(), MOSQUITTO.getMappedPort(1883));
        termostatos = new Termostatos("tcp://" + MOSQUITTO.getHost() + ":" + MOSQUITTO.getMappedPort(1883));
    }

    @AfterAll
    static void bajarSimuladorYTermostatos() throws Exception {
        termostatos.close();
        simulador.close();
    }

    /** Control detenido, switches apagados, el sitio de 3 habitaciones cargado y todas tibias. */
    @BeforeEach
    void casaEnReposo() {
        controlar(Map.of("accion", "DETENER"));
        simulador.reiniciar();
        cargarSitio(inventario(ROOM1, ROOM2, ROOM3));
        medir(ROOM1, TIBIA);
        medir(ROOM2, TIBIA);
        medir(ROOM3, TIBIA);
    }

    // ---------------------------------------------------------------- escenarios normales

    @Test
    void alArrancarSeRegistraEnElSimuladorYEscuchaLosTopicsDelInventario() {
        assertThat(simulador.registros()).anySatisfy(alta -> {
            assertThat(alta.get("etiqueta")).isEqualTo("control-grupo-3");
            assertThat(alta.get("apiKey")).isEqualTo(CLAVE_ENGINE);
        });
        // Las lecturas llegaron por el broker que anunció el simulador, también la del topic fuera de la convención
        assertThat(ultimasTemperaturas()).containsOnlyKeys("room1", "room2", "room3");
        assertThat(llamar(HttpMethod.GET, "/control", null).getBody()).contains("\"estado\":\"DETENIDO\"");
    }

    @Test
    void conFrioEnciendeYApagaRecienAlSuperarElObjetivoPorElMargen() {
        iniciar(SABADO_9HS, X600);
        medir(ROOM1, FRIA);
        esperarEstado(ROOM1, "ON");
        // 3 s reales son 30 minutos simulados: con lecturas cada 0.5 s, ningún apagado falso del fail-safe
        sigueEn(ROOM1, "ON", Duration.ofSeconds(3));

        medir(ROOM1, 21.2); // pasó la esperada (21), pero no el margen de 0.5
        sigueEn(ROOM1, "ON", Duration.ofSeconds(2));

        medir(ROOM1, 21.6);
        esperarEstado(ROOM1, "OFF");
        assertThat(acciones(ROOM1)).containsExactly("ON", "OFF");
        assertThat(acciones(ROOM2)).isEmpty();
        assertThat(acciones(ROOM3)).isEmpty();
    }

    @Test
    void repartePotenciaContratadaEmpezandoPorLaHabitacionMasLejosDeSuObjetivo() {
        // Con frío las tres suman 3.5 kW y hay 3.0 contratados. Faltan 6 °C en room1, 2 en room2 y 4 en room3
        medir(ROOM1, FRIA);
        medir(ROOM2, 19.0);
        medir(ROOM3, 17.0);
        iniciar(SABADO_9HS, X600);
        esperarEstado(ROOM1, "ON");
        esperarEstado(ROOM3, "ON");
        sigueEn(ROOM2, "OFF", Duration.ofSeconds(2));

        medir(ROOM1, 22.0); // room1 llegó: se apaga y libera potencia para room2
        esperarEstado(ROOM1, "OFF");
        esperarEstado(ROOM2, "ON");
        // Primero se apaga y después se enciende: ni por un instante se pasó de lo contratado
        assertThat(simulador.potenciaMaxima()).isLessThanOrEqualTo(POTENCIA_CONTRATADA);
    }

    @Test
    void cadaSwitchSeComandaEnElUrlSwitchDeSuHabitacion() {
        medir(ROOM1, FRIA);
        medir(ROOM3, FRIA);
        iniciar(SABADO_9HS, X600);
        esperarEstado(ROOM1, "ON");
        esperarEstado(ROOM3, "ON");

        assertThat(simulador.comandos(ROOM1.idSwitch())).extracting(Comando::ruta).containsOnly("");
        assertThat(simulador.comandos(ROOM3.idSwitch())).extracting(Comando::ruta).containsOnly("/edificio-b");
    }

    @Test
    void noConsultaCadaSwitchEnCadaCicloPeroDetectaUnCambioHechoAMano() {
        iniciar(SABADO_9HS, X600);
        // 6 s a ×600 son 12 ciclos: antes eran 12 consultas por switch; ahora una cada 5 s como mucho
        await().pollDelay(Duration.ofSeconds(6)).until(() -> true);
        for (Hab hab : List.of(ROOM1, ROOM2, ROOM3)) {
            assertThat(simulador.consultas(hab.idSwitch())).as("consultas a %s", hab.idSwitch()).isBetween(1L, 3L);
        }

        // Alguien lo enciende desde la consola del simulador: está tibia, así que el engine lo vuelve a apagar
        simulador.accionarAMano(ROOM1.idSwitch(), "ON");
        esperarEstado(ROOM1, "OFF", Duration.ofSeconds(8));
        assertThat(acciones(ROOM1)).containsExactly("OFF");
    }

    @Test
    void antesDeLas17DeUnDiaHabilApagaTodoParaQueNadaQuedeEncendidoEnLaPunta() {
        medir(ROOM1, FRIA);
        Instant antesDeIniciar = Instant.now();
        iniciar("2026-10-27T16:55:00-03:00", 60); // martes: a ×60, las 17:00 simuladas llegan 5 s después
        Instant despuesDeIniciar = Instant.now();
        esperarEstado(ROOM1, "ON", Duration.ofSeconds(3));

        esperarEstado(ROOM1, "OFF", Duration.ofSeconds(10));
        // Se apaga en el último ciclo antes de las 17:00, no en el primero después (ecowarm-run E11: en punta
        // no se enciende nada), y tampoco mucho antes
        assertThat(ultimoComando(ROOM1).en())
                .isAfter(antesDeIniciar.plusSeconds(3))
                .isBefore(despuesDeIniciar.plusSeconds(5));
        sigueEn(ROOM1, "OFF", Duration.ofSeconds(2)); // sigue con frío, pero en punta no se enciende
    }

    // ---------------------------------------------------------------- escenarios de falla

    @Test
    void unTermostatoQueDejaDePublicarApagaSuHabitacionPorSeguridadYLasDemasSiguen() {
        medir(ROOM1, FRIA);
        medir(ROOM2, FRIA);
        iniciar(SABADO_9HS, X600);
        esperarEstado(ROOM1, "ON");
        esperarEstado(ROOM2, "ON");

        termostatos.callar(ROOM1.topic());
        Instant callado = Instant.now();
        esperarEstado(ROOM1, "OFF", Duration.ofSeconds(8));
        // Publicaba cada 0.5 s: se da por caído tras 3 lecturas perdidas (con el piso de 2 s), no antes
        assertThat(Duration.between(callado, ultimoComando(ROOM1).en())).isGreaterThan(Duration.ofMillis(1400));
        assertThat(simulador.estado(ROOM2.idSwitch())).isEqualTo("ON");

        medir(ROOM1, FRIA); // vuelve a publicar
        esperarEstado(ROOM1, "ON");
        assertThat(acciones(ROOM2)).containsExactly("ON");
    }

    @Test
    void conElSimuladorCaidoElControlSigueYAlVolverCorrigeLosSwitches() {
        medir(ROOM1, FRIA);
        iniciar(SABADO_9HS, X600);
        esperarEstado(ROOM1, "ON");

        simulador.caer();
        medir(ROOM1, TIBIA); // ya no necesita calor, pero no hay cómo apagarlo
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(4))
                .until(() -> llamar(HttpMethod.GET, "/control", null).getBody().contains("EN_EJECUCION"));
        assertThat(simulador.estado(ROOM1.idSwitch())).isEqualTo("ON");

        simulador.levantar();
        esperarEstado(ROOM1, "OFF");
    }

    @Test
    void sinBrokerApagaPorSeguridadYAlVolverSeReconectaSoloYSigueControlando() {
        medir(ROOM1, FRIA);
        iniciar(SABADO_9HS, X600);
        esperarEstado(ROOM1, "ON");

        caer(MOSQUITTO);
        esperarEstado(ROOM1, "OFF", Duration.ofSeconds(8)); // sin lecturas, el fail-safe la apaga
        levantar(MOSQUITTO);

        // Solo vuelve a encenderse con lecturas nuevas: el engine se reconectó y se volvió a suscribir
        esperarEstado(ROOM1, "ON", Duration.ofSeconds(40));
    }

    @Test
    void sinBaseLaApiSigueRespondiendoYAlVolverElControlSeRecuperaSoloYConservaElSitio() {
        medir(ROOM1, FRIA);
        iniciar(SABADO_9HS, X600);
        esperarEstado(ROOM1, "ON");

        caer(POSTGRES);
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5))
                .until(() -> llamar(HttpMethod.GET, "/control", null).getBody().contains("EN_EJECUCION"));
        levantar(POSTGRES);

        // Las lecturas se vuelven a guardar y el ciclo vuelve a decidir con ellas
        medir(ROOM1, TIBIA);
        esperarEstado(ROOM1, "OFF");
        medir(ROOM1, FRIA);
        esperarEstado(ROOM1, "ON");
        assertThat(llamar(HttpMethod.GET, "/sitio", null).getBody()).contains("\"room1\"", "\"room2\"", "\"room3\"");
    }

    @Test
    void unaHabitacionQueSaleDelInventarioNoSeVuelveAComandarPorqueYaNoEsDelSitio() {
        medir(ROOM3, FRIA);
        iniciar(SABADO_9HS, X600);
        esperarEstado(ROOM3, "ON");

        cargarSitio(inventario(ROOM1, ROOM2)); // el simulador saca room3 del inventario
        medir(ROOM1, FRIA);
        esperarEstado(ROOM1, "ON"); // el control ya trabaja con el sitio nuevo
        long consultasDeRoom3 = simulador.consultas(ROOM3.idSwitch());

        // Solo se comandan los switches del sitio vigente (ecowarm-run: soloSwitchesDelSitio). Ni se apaga ni
        // se consulta: ya no es de este controlador
        sigueEn(ROOM3, "ON", Duration.ofSeconds(2));
        assertThat(acciones(ROOM3)).containsExactly("ON");
        assertThat(simulador.consultas(ROOM3.idSwitch())).isEqualTo(consultasDeRoom3);
    }

    @Test
    void siBajaLaContratadaEnCalienteApagaLoQueSobra() {
        medir(ROOM1, FRIA);
        medir(ROOM2, FRIA);
        iniciar(SABADO_9HS, X600);
        esperarEstado(ROOM1, "ON");
        esperarEstado(ROOM2, "ON"); // 2.5 kW de 3.0

        cargarSitio(inventario(1.2, ROOM1, ROOM2, ROOM3)); // PUT /sitio en caliente: ahora caben 1.2 kW

        // Lo encendido ya no entra: se apaga lo que sobra (ecowarm-run E18) y queda room2, que sí entra
        await().atMost(UN_PAR_DE_CICLOS).untilAsserted(() ->
                assertThat(simulador.potenciaEncendida()).isLessThanOrEqualTo(1.2));
        esperarEstado(ROOM2, "ON");
        sigueEn(ROOM1, "OFF", Duration.ofSeconds(2));
    }

    @Test
    void siLosOffNoLleganNoEnciendeOtraHastaPoderApagarSinPasarseDeLaContratada() {
        // Con frío entran room1 y room2 (2.5 kW); room3 (1 kW más) espera
        medir(ROOM1, FRIA);
        medir(ROOM2, FRIA);
        medir(ROOM3, FRIA);
        iniciar(SABADO_9HS, X600);
        esperarEstado(ROOM1, "ON");
        esperarEstado(ROOM2, "ON");
        sigueEn(ROOM3, "OFF", Duration.ofSeconds(1));

        simulador.perderLosOff(true); // ecowarm-run F05: la conexión de cada OFF queda colgada
        medir(ROOM1, 22.0); // room1 llegó: hay que apagarla para encender room3, pero el OFF no llega
        sigueEn(ROOM3, "OFF", Duration.ofSeconds(4));

        simulador.perderLosOff(false);
        esperarEstado(ROOM1, "OFF");
        esperarEstado(ROOM3, "ON");
        // Mientras el OFF no llegaba, room1 se contó encendida: nunca se pasó de lo contratado
        assertThat(simulador.potenciaMaxima()).isLessThanOrEqualTo(POTENCIA_CONTRATADA);
    }

    // ---------------------------------------------------------------- ayudas

    private static InventarioDTO inventario(Hab... habitaciones) {
        return inventario(POTENCIA_CONTRATADA, habitaciones);
    }

    private static InventarioDTO inventario(double potenciaContratada, Hab... habitaciones) {
        Sitio sitio = new Sitio("casa-e2e", "Casa de prueba", potenciaContratada,
                new Tarifa(new Franja("17:00", "23:00", "HABILES")));
        return new InventarioDTO("2.0", sitio, Arrays.stream(habitaciones)
                .map(h -> new Habitacion(h.id(), "Cuarto " + h.id(), ESPERADA, h.potenciaKW(), "ht-" + h.id(),
                        h.topic(), h.idSwitch(), simulador.url() + h.ruta()))
                .toList());
    }

    /** {@code PUT /sitio}, como lo haría el simulador (§5.1). */
    private void cargarSitio(InventarioDTO inventario) {
        assertThat(llamar(HttpMethod.PUT, "/sitio", inventario).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void iniciar(String fechaHora, double factor) {
        controlar(Map.of("accion", "INICIAR", "fechaHora", fechaHora, "factorTiempo", factor));
    }

    private void controlar(Map<String, Object> comando) {
        assertThat(llamar(HttpMethod.POST, "/control/comandos", comando).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> llamar(HttpMethod metodo, String ruta, Object cuerpo) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.set("X-API-Key", CLAVE_ENGINE);
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(ruta, metodo, new HttpEntity<>(cuerpo, cabeceras), String.class);
    }

    /** El termostato pasa a medir esa temperatura; vuelve cuando el engine ya la guardó como última lectura. */
    private void medir(Hab hab, double temperaturaC) {
        termostatos.medir(hab.topic(), temperaturaC);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(ultimasTemperaturas()).containsEntry(hab.id(), temperaturaC));
    }

    private Map<String, Double> ultimasTemperaturas() {
        Map<String, Double> temperaturas = new HashMap<>();
        historico.ultimasLecturas().forEach((id, lectura) -> temperaturas.put(id, lectura.temperaturaC()));
        return temperaturas;
    }

    private static void esperarEstado(Hab hab, String estado) {
        esperarEstado(hab, estado, UN_PAR_DE_CICLOS);
    }

    private static void esperarEstado(Hab hab, String estado, Duration plazo) {
        await().atMost(plazo).untilAsserted(() ->
                assertThat(simulador.estado(hab.idSwitch())).as("switch de %s", hab.id()).isEqualTo(estado));
    }

    /** El switch se mantiene en ese estado durante todo el lapso. */
    private static void sigueEn(Hab hab, String estado, Duration lapso) {
        await().during(lapso).atMost(lapso.plusSeconds(1)).until(() -> estado.equals(simulador.estado(hab.idSwitch())));
    }

    private static List<String> acciones(Hab hab) {
        return simulador.comandos(hab.idSwitch()).stream().map(Comando::accion).toList();
    }

    private static Comando ultimoComando(Hab hab) {
        List<Comando> comandos = simulador.comandos(hab.idSwitch());
        return comandos.get(comandos.size() - 1);
    }

    /** Detiene el contenedor sin borrarlo: al {@link #levantar} vuelve con el mismo puerto y los mismos datos. */
    private static void caer(GenericContainer<?> contenedor) {
        contenedor.getDockerClient().stopContainerCmd(contenedor.getContainerId()).withTimeout(1).exec();
    }

    private static void levantar(GenericContainer<?> contenedor) {
        contenedor.getDockerClient().startContainerCmd(contenedor.getContainerId()).exec();
    }

    private static void fijarPuerto(GenericContainer<?> contenedor, int puertoInterno) {
        try (ServerSocket libre = new ServerSocket(0)) {
            contenedor.setPortBindings(List.of(libre.getLocalPort() + ":" + puertoInterno));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
