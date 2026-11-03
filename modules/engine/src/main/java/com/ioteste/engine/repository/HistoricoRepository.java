package com.ioteste.engine.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Histórico de temperaturas que llegan por MQTT. La columna {@code fecha_hora} no tiene zona horaria y
 * por convención guarda UTC: se escribe y se lee siempre en UTC, sin depender de la zona de la JVM.
 */
@Repository
public class HistoricoRepository {

    /** Última lectura por habitación: una búsqueda por índice por habitación, sin recorrer el histórico. */
    static final String SQL_ULTIMAS_LECTURAS = """
            SELECT h.id, ult.fecha_hora, ult.temperatura_c
            FROM habitacion h
            JOIN LATERAL (
                SELECT fecha_hora, temperatura_c FROM historico_temperatura
                WHERE habitacion_id = h.id ORDER BY fecha_hora DESC LIMIT 1
            ) ult ON true
            """;

    private final JdbcTemplate jdbc;

    public HistoricoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Registra la lectura para las habitaciones que declaran ese topic. Devuelve cuántas filas grabó. */
    public int registrar(String topic, Instant fechaHora, double temperaturaC) {
        return jdbc.update("""
                INSERT INTO historico_temperatura (habitacion_id, fecha_hora, temperatura_c)
                SELECT id, ?, ? FROM habitacion WHERE topic_termostato = ?
                """, LocalDateTime.ofInstant(fechaHora, ZoneOffset.UTC), temperaturaC, topic);
    }

    /** Última lectura de cada habitación que tenga alguna, indexada por id de habitación. */
    public Map<String, Lectura> ultimasLecturas() {
        Map<String, Lectura> lecturas = new HashMap<>();
        jdbc.query(SQL_ULTIMAS_LECTURAS, rs -> {
            lecturas.put(rs.getString("id"), new Lectura(
                    rs.getObject("fecha_hora", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                    rs.getDouble("temperatura_c")));
        });
        return lecturas;
    }

    /** Una lectura de temperatura. */
    public record Lectura(Instant fechaHora, double temperaturaC) { }
}
