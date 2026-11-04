package com.ioteste.engine.repository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Histórico de temperaturas que llegan por MQTT. Las columnas de fecha no tienen zona horaria y por
 * convención guardan UTC: se escriben y se leen siempre en UTC, sin depender de la zona de la JVM.
 */
@Repository
public class HistoricoRepository {

    /**
     * Última lectura por habitación y el ritmo al que publica su termostato: la mediana de los huecos entre
     * las llegadas de sus últimas 5 lecturas. La mediana no se deja llevar por un corte aislado (un hueco
     * largo) ni por un mensaje repetido (un hueco de casi cero). Son búsquedas por índice por habitación, sin
     * recorrer el histórico. Las filas grabadas antes de que existiera {@code recibida_en} usan su
     * {@code fecha_hora}.
     *
     * <p>"Última" es la que llegó última, no la de hora simulada mayor: cada {@code INICIAR} puede volver el
     * reloj simulado atrás, y las lecturas de una corrida anterior, con horas más tardías, taparían a las nuevas.
     */
    static final String SQL_ULTIMAS_LECTURAS = """
            SELECT h.id, ult.fecha_hora, ult.recibida_en, ult.temperatura_c, ritmo.segundos AS ritmo_s
            FROM habitacion h
            JOIN LATERAL (
                SELECT fecha_hora, COALESCE(recibida_en, fecha_hora) AS recibida_en, temperatura_c
                FROM historico_temperatura
                WHERE habitacion_id = h.id ORDER BY COALESCE(recibida_en, fecha_hora) DESC LIMIT 1
            ) ult ON true
            CROSS JOIN LATERAL (
                SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY hueco) AS segundos
                FROM (
                    SELECT EXTRACT(EPOCH FROM llegada - lag(llegada) OVER (ORDER BY llegada))::float8 AS hueco
                    FROM (
                        SELECT COALESCE(recibida_en, fecha_hora) AS llegada
                        FROM historico_temperatura
                        WHERE habitacion_id = h.id ORDER BY COALESCE(recibida_en, fecha_hora) DESC LIMIT 5
                    ) recientes
                ) huecos
                WHERE hueco IS NOT NULL
            ) ritmo
            """;

    private final JdbcTemplate jdbc;

    public HistoricoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Registra la lectura para las habitaciones que declaran ese topic. {@code medidaEn} es el instante de
     * la medición ({@code ts} del termostato); {@code recibidaEn}, cuándo llegó al engine (hora real).
     * Devuelve cuántas filas grabó.
     */
    public int registrar(String topic, Instant medidaEn, Instant recibidaEn, double temperaturaC) {
        return jdbc.update("""
                INSERT INTO historico_temperatura (habitacion_id, fecha_hora, recibida_en, temperatura_c)
                SELECT id, ?, ?, ? FROM habitacion WHERE topic_termostato = ?
                """, utc(medidaEn), utc(recibidaEn), temperaturaC, topic);
    }

    /** Última lectura de cada habitación que tenga alguna, indexada por id de habitación. */
    public Map<String, Lectura> ultimasLecturas() {
        Map<String, Lectura> lecturas = new HashMap<>();
        jdbc.query(SQL_ULTIMAS_LECTURAS, rs -> {
            Double ritmoSegundos = rs.getObject("ritmo_s", Double.class);
            lecturas.put(rs.getString("id"), new Lectura(
                    rs.getObject("fecha_hora", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                    rs.getObject("recibida_en", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                    rs.getDouble("temperatura_c"),
                    ritmoSegundos == null ? null : Duration.ofMillis(Math.round(ritmoSegundos * 1000))));
        });
        return lecturas;
    }

    private static LocalDateTime utc(Instant instante) {
        return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    /**
     * Una lectura de temperatura: cuándo se midió, cuándo llegó al engine (hora real), el valor y cada cuánto
     * (en tiempo real) publica ese termostato. {@code ritmo} es {@code null} mientras haya una sola lectura.
     */
    public record Lectura(Instant fechaHora, Instant recibidaEn, double temperaturaC, Duration ritmo) {

        /** Una lectura de un termostato del que todavía no se conoce el ritmo. */
        public Lectura(Instant fechaHora, Instant recibidaEn, double temperaturaC) {
            this(fechaHora, recibidaEn, temperaturaC, null);
        }
    }
}
