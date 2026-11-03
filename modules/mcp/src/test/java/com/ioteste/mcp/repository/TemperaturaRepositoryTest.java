package com.ioteste.mcp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ioteste.mcp.repository.TemperaturaRepository.Lectura;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.TimeZone;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Lectura del histórico contra un PostgreSQL real con el esquema del engine. Los datos se cargan con una
 * conexión aparte porque el pool del MCP es de solo lectura. Sin Docker se omite.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TemperaturaRepository.class)
@Testcontainers(disabledWithoutDocker = true)
class TemperaturaRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired private TemperaturaRepository repository;

    @BeforeAll
    static void cargarDatos() throws Exception {
        try (Connection con = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            ScriptUtils.executeSqlScript(con, new FileSystemResource("../engine/src/main/resources/schema.sql"));
            con.createStatement().execute("""
                    INSERT INTO sitio (id, nombre, potencia_contratada_kw, tarifa_desde, tarifa_hasta, tarifa_dias)
                    VALUES ('casa', 'Casa', 3.7, '17:00', '23:00', 'HABILES');
                    INSERT INTO habitacion (id, sitio_id, nombre, temperatura_esperada, potencia_kw, id_termostato,
                                            topic_termostato, id_switch, url_switch)
                    VALUES ('room1', 'casa', 'Living', 21.5, 1.2, 'ht-1', 't1', 'sw-1', 'http://sim'),
                           ('room2', 'casa', 'Cuarto', 20.0, 0.8, 'ht-2', 't2', 'sw-2', 'http://sim');
                    INSERT INTO historico_temperatura (habitacion_id, fecha_hora, temperatura_c)
                    VALUES ('room1', '2026-10-01 09:15:00', 18.0),
                           ('room1', '2026-10-01 09:15:30', 18.4),
                           ('room1', '2026-10-01 09:14:30', 17.0);
                    """);
        }
    }

    @Test
    void ultimasLecturasDevuelveLaMasRecienteDeCadaHabitacionConLecturas() {
        assertThat(repository.ultimasLecturas()).containsOnlyKeys("room1");
        assertThat(repository.ultimasLecturas().get("room1"))
                .isEqualTo(new Lectura("room1", Instant.parse("2026-10-01T09:15:30Z"), 18.4));
    }

    @Test
    void historialOrdenadoDeLaMasNuevaALaMasViejaConLimite() {
        assertThat(repository.historial("room1", 2)).extracting(Lectura::temperaturaC).containsExactly(18.4, 18.0);
        assertThat(repository.historial("room2", 10)).isEmpty();
    }

    @Test
    void lasFechasSeLeenEnUtcSinImportarLaZonaDeLaJvm() {
        TimeZone zonaOriginal = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Montevideo"));
        try {
            assertThat(repository.historial("room1", 1).get(0).fechaHora())
                    .isEqualTo(Instant.parse("2026-10-01T09:15:30Z"));
        } finally {
            TimeZone.setDefault(zonaOriginal);
        }
    }
}
