package com.ioteste.engine.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ioteste.engine.domain.Franja;
import com.ioteste.engine.domain.Habitacion;
import com.ioteste.engine.domain.InventarioDTO;
import com.ioteste.engine.domain.Sitio;
import com.ioteste.engine.domain.Tarifa;
import com.ioteste.engine.repository.HistoricoRepository.Lectura;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.TimeZone;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Repositorios contra un PostgreSQL real (el SQL usa ON CONFLICT, arrays y LATERAL). Cada test corre en
 * una transacción que se revierte al terminar. Sin Docker (por ejemplo dentro de {@code docker build})
 * se omite.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SitioRepository.class, HistoricoRepository.class})
@Testcontainers(disabledWithoutDocker = true)
class RepositoriosPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    private static final Instant T1 = Instant.parse("2026-10-01T09:15:03.120Z");
    private static final Instant RECIBIDA = Instant.parse("2026-10-01T09:15:05.500Z");

    @Autowired private SitioRepository sitios;
    @Autowired private HistoricoRepository historico;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    // ---------------------------------------------------------------- inventario

    @Test
    void sinSitioCargadoNoHayInventario() {
        assertThat(sitios.obtenerInventario()).isEmpty();
    }

    @Test
    void devuelveExactamenteLoQueSeCargo() {
        InventarioDTO cargado = inventario("2.1", "casa", habitacion("room2", 20.0), habitacion("room1", 21.5));

        sitios.guardarInventario(cargado);

        // Mismo documento: horas en HH:mm, versión y orden de las habitaciones incluidos
        assertThat(sitios.obtenerInventario()).contains(cargado);
    }

    @Test
    void reemplazarConservaElHistoricoDeLasHabitacionesQueSiguen() {
        sitios.guardarInventario(inventario("2.0", "casa", habitacion("room1", 20.0), habitacion("room2", 20.0)));
        historico.registrar("ht-room1/status/temperature:0", T1, RECIBIDA, 18.5);
        historico.registrar("ht-room2/status/temperature:0", T1, RECIBIDA, 19.0);

        InventarioDTO nuevo = inventario("2.0", "casa", habitacion("room3", 22.0), habitacion("room1", 23.0));
        sitios.guardarInventario(nuevo);

        assertThat(sitios.obtenerInventario()).contains(nuevo);
        assertThat(historico.ultimasLecturas()).containsOnlyKeys("room1");
        assertThat(historico.ultimasLecturas().get("room1")).isEqualTo(new Lectura(T1, RECIBIDA, 18.5));
    }

    @Test
    void unSitioConOtroIdReemplazaAlAnterior() {
        sitios.guardarInventario(inventario("2.0", "viejo", habitacion("room1", 20.0)));
        InventarioDTO nuevo = inventario("2.0", "nuevo", habitacion("room9", 20.0));

        sitios.guardarInventario(nuevo);

        assertThat(sitios.obtenerInventario()).contains(nuevo);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM habitacion", Integer.class)).isEqualTo(1);
        assertThat(sitios.switchesPendientesDeApagar()).containsExactly("sw-room1");
    }

    // ---------------------------------------------------------------- switches pendientes de apagar

    @Test
    void elPrimerInventarioNoDejaNadaPendiente() {
        sitios.guardarInventario(inventario("2.0", "casa", habitacion("room1", 20.0)));

        assertThat(sitios.switchesPendientesDeApagar()).isEmpty();
    }

    @Test
    void alSacarUnaHabitacionSuSwitchQuedaAnotadoEnLaBaseHastaApagarlo() {
        sitios.guardarInventario(inventario("2.0", "casa", habitacion("room1", 20.0), habitacion("room2", 20.0)));

        sitios.guardarInventario(inventario("2.0", "casa", habitacion("room1", 20.0)));
        assertThat(sitios.switchesPendientesDeApagar()).containsExactly("sw-room2");

        sitios.quitarPendienteDeApagar("sw-room2");
        assertThat(sitios.switchesPendientesDeApagar()).isEmpty();
    }

    @Test
    void siLaHabitacionVuelveAlInventarioDejaDeEstarPendiente() {
        sitios.guardarInventario(inventario("2.0", "casa", habitacion("room1", 20.0), habitacion("room2", 20.0)));
        sitios.guardarInventario(inventario("2.0", "casa", habitacion("room1", 20.0)));

        sitios.guardarInventario(inventario("2.0", "casa", habitacion("room1", 20.0), habitacion("room2", 20.0)));

        assertThat(sitios.switchesPendientesDeApagar()).isEmpty();
    }

    @Test
    void sinHabitacionesSeBorranTodas() {
        sitios.guardarInventario(inventario("2.0", "casa", habitacion("room1", 20.0)));
        InventarioDTO vacio = inventario("2.0", "casa");

        sitios.guardarInventario(vacio);

        assertThat(sitios.obtenerInventario()).contains(vacio);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void unaFallaAMitadDeLaCargaNoDejaElSitioAMedioConfigurar() {
        InventarioDTO original = inventario("2.0", "casa", habitacion("room1", 20.0));
        sitios.guardarInventario(original);
        try {
            Habitacion nombreDemasiadoLargo = new Habitacion("room2", "x".repeat(101), 20.0, 1.0, "ht-room2",
                    "ht-room2/status/temperature:0", "sw-room2", "http://sim:9090");
            InventarioDTO roto = inventario("2.0", "otra-casa", habitacion("room3", 20.0), nombreDemasiadoLargo);

            assertThatThrownBy(() -> sitios.guardarInventario(roto))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(sitios.obtenerInventario()).contains(original);
            assertThat(sitios.switchesPendientesDeApagar()).isEmpty(); // también se revirtió lo anotado
        } finally {
            jdbc.update("DELETE FROM sitio");
            jdbc.update("DELETE FROM switch_pendiente_apagado");
        }
    }

    // ---------------------------------------------------------------- histórico

    @Test
    void registraLaLecturaEnTodasLasHabitacionesDelTopic() {
        Habitacion a = habitacion("room1", 20.0);
        Habitacion b = new Habitacion("room2", "Cuarto", 20.0, 1.0, "ht-room2", a.topicTermostato(), "sw-room2",
                "http://sim:9090");
        sitios.guardarInventario(inventario("2.0", "casa", a, b));

        assertThat(historico.registrar(a.topicTermostato(), T1, RECIBIDA, 19.5)).isEqualTo(2);
        assertThat(historico.registrar("ht-otro/status/temperature:0", T1, RECIBIDA, 19.5)).isZero();
    }

    @Test
    void ultimasLecturasDevuelveLaMasRecienteDeCadaHabitacion() {
        sitios.guardarInventario(inventario("2.0", "casa", habitacion("room1", 20.0), habitacion("room2", 20.0)));
        historico.registrar("ht-room1/status/temperature:0", T1, RECIBIDA, 18.0);
        historico.registrar("ht-room1/status/temperature:0", T1.plusSeconds(30), RECIBIDA.plusSeconds(5), 18.4);
        historico.registrar("ht-room1/status/temperature:0", T1.minusSeconds(30), RECIBIDA.plusSeconds(10), 17.0);

        assertThat(historico.ultimasLecturas()).containsOnlyKeys("room1");
        assertThat(historico.ultimasLecturas().get("room1"))
                .isEqualTo(new Lectura(T1.plusSeconds(30), RECIBIDA.plusSeconds(5), 18.4, Duration.ofSeconds(5)));
    }

    @Test
    void elRitmoDelTermostatoEsLaMedianaDeLosHuecosEntreSusUltimasCincoLlegadas() {
        sitios.guardarInventario(inventario("2.0", "casa", habitacion("room1", 20.0), habitacion("room2", 20.0)));
        // room1: una lectura vieja cada 60 s (queda fuera de las últimas 5), después cada 5 s con un corte de 2 min
        int[] llegadas = {0, 60, 120, 125, 130, 135, 255};
        for (int segundo : llegadas) {
            historico.registrar("ht-room1/status/temperature:0", T1.plusSeconds(segundo),
                    RECIBIDA.plusSeconds(segundo), 20.0);
        }
        // room2: una sola lectura, todavía no hay ritmo
        historico.registrar("ht-room2/status/temperature:0", T1, RECIBIDA, 20.0);

        // Últimas 5 llegadas: 120, 125, 130, 135, 255 → huecos 5, 5, 5, 120 → el corte no mueve la mediana
        assertThat(historico.ultimasLecturas().get("room1").ritmo()).isEqualTo(Duration.ofSeconds(5));
        assertThat(historico.ultimasLecturas().get("room2").ritmo()).isNull();
    }

    @Test
    void unMensajeRepetidoNoAchicaElRitmo() {
        sitios.guardarInventario(inventario("2.0", "casa", habitacion("room1", 20.0)));
        double[] llegadas = {0, 5, 10, 10.05, 15};
        for (double segundo : llegadas) {
            Instant llegada = RECIBIDA.plusMillis((long) (segundo * 1000));
            historico.registrar("ht-room1/status/temperature:0", llegada, llegada, 20.0);
        }

        // Huecos 5, 5, 0.05, 4.95 → mediana 4.975 s
        assertThat(historico.ultimasLecturas().get("room1").ritmo()).isEqualTo(Duration.ofMillis(4975));
    }

    @Test
    void lasLecturasGrabadasAntesDeLaMigracionUsanSuFechaComoLlegada() {
        sitios.guardarInventario(inventario("2.0", "casa", habitacion("room1", 20.0)));
        jdbc.update("INSERT INTO historico_temperatura (habitacion_id, fecha_hora, temperatura_c) "
                + "VALUES ('room1', '2026-10-01 09:15:03.12', 19.0)");

        assertThat(historico.ultimasLecturas().get("room1")).isEqualTo(new Lectura(T1, T1, 19.0));
    }

    @Test
    void lasFechasSeGuardanEnUtcSinImportarLaZonaDeLaJvm() {
        sitios.guardarInventario(inventario("2.0", "casa", habitacion("room1", 20.0)));
        TimeZone zonaOriginal = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Montevideo"));
        try {
            historico.registrar("ht-room1/status/temperature:0", T1, RECIBIDA, 20.0);

            assertThat(jdbc.queryForObject("SELECT fecha_hora::text || ' / ' || recibida_en::text "
                    + "FROM historico_temperatura", String.class))
                    .isEqualTo("2026-10-01 09:15:03.12 / 2026-10-01 09:15:05.5");
            assertThat(historico.ultimasLecturas().get("room1")).isEqualTo(new Lectura(T1, RECIBIDA, 20.0));
        } finally {
            TimeZone.setDefault(zonaOriginal);
        }
    }

    @Test
    void laConsultaDeUltimasLecturasUsaElIndice() {
        jdbc.execute("SET LOCAL enable_seqscan = off");

        List<String> plan = jdbc.queryForList("EXPLAIN " + HistoricoRepository.SQL_ULTIMAS_LECTURAS, String.class);

        assertThat(String.join("\n", plan)).contains("idx_historico_habitacion_fecha");
    }

    // ---------------------------------------------------------------- esquema

    @Test
    void elEsquemaEsIdempotenteYActualizaBasesDeVersionesAnteriores() {
        // Una base creada por la versión anterior (init.sql) no tenía estas columnas ni el índice
        jdbc.execute("DROP INDEX idx_historico_habitacion_fecha");
        jdbc.execute("ALTER TABLE sitio DROP COLUMN schema_version");
        jdbc.execute("ALTER TABLE habitacion DROP COLUMN orden");
        jdbc.execute("ALTER TABLE historico_temperatura DROP COLUMN recibida_en");
        jdbc.execute("DROP TABLE switch_pendiente_apagado");

        ejecutarSchemaSql();
        ejecutarSchemaSql();

        sitios.guardarInventario(inventario("2.3", "casa", habitacion("room1", 20.0)));
        assertThat(sitios.obtenerInventario().orElseThrow().schemaVersion()).isEqualTo("2.3");
        assertThat(historico.registrar("ht-room1/status/temperature:0", T1, RECIBIDA, 20.0)).isEqualTo(1);
        sitios.guardarInventario(inventario("2.3", "casa"));
        assertThat(sitios.switchesPendientesDeApagar()).containsExactly("sw-room1");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_historico_habitacion_fecha'", Integer.class))
                .isEqualTo(1);
    }

    // ---------------------------------------------------------------- auxiliares

    private void ejecutarSchemaSql() {
        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource), new ClassPathResource("schema.sql"));
    }

    private static InventarioDTO inventario(String version, String idSitio, Habitacion... habitaciones) {
        return new InventarioDTO(version,
                new Sitio(idSitio, "Casa " + idSitio, 3.7, new Tarifa(new Franja("17:00", "23:00", "HABILES"))),
                List.of(habitaciones));
    }

    private static Habitacion habitacion(String id, double esperada) {
        return new Habitacion(id, "Cuarto " + id, esperada, 1.2, "ht-" + id, "ht-" + id + "/status/temperature:0",
                "sw-" + id, "http://iiss-simulator:9090");
    }
}
