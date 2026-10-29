package com.ioteste.mcp.repository;

import com.ioteste.mcp.client.ServicioExternoException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Acceso de solo lectura al histórico de temperaturas que registra el engine a partir de los
 * mensajes MQTT de los termostatos (tabla {@code historico_temperatura}).
 */
@Repository
public class TemperaturaRepository {

    private static final String SQL_ULTIMAS = """
            SELECT DISTINCT ON (habitacion_id) habitacion_id, fecha_hora, temperatura_c
            FROM historico_temperatura
            ORDER BY habitacion_id, fecha_hora DESC
            """;

    private static final String SQL_HISTORIAL = """
            SELECT habitacion_id, fecha_hora, temperatura_c
            FROM historico_temperatura
            WHERE habitacion_id = ?
            ORDER BY fecha_hora DESC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public TemperaturaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Última lectura registrada de cada habitación, indexada por id de habitación. */
    public Map<String, Lectura> ultimasLecturas() {
        try {
            Map<String, Lectura> resultado = new HashMap<>();
            jdbcTemplate.query(SQL_ULTIMAS, rs -> {
                Lectura lectura = mapear(rs);
                resultado.put(lectura.habitacionId(), lectura);
            });
            return resultado;
        } catch (DataAccessException e) {
            throw errorBd(e);
        }
    }

    /** Las {@code limite} lecturas más recientes de una habitación, de la más nueva a la más vieja. */
    public List<Lectura> historial(String habitacionId, int limite) {
        try {
            return jdbcTemplate.query(SQL_HISTORIAL, (rs, n) -> mapear(rs), habitacionId, limite);
        } catch (DataAccessException e) {
            throw errorBd(e);
        }
    }

    private static Lectura mapear(ResultSet rs) throws SQLException {
        return new Lectura(
                rs.getString("habitacion_id"),
                rs.getTimestamp("fecha_hora").toInstant(),
                rs.getDouble("temperatura_c"));
    }

    private static ServicioExternoException errorBd(DataAccessException e) {
        return new ServicioExternoException(
                "No se pudo leer el histórico de temperaturas de la base de datos: "
                        + e.getMostSpecificCause().getMessage(), e);
    }

    /** Una lectura de temperatura de un termostato. */
    public record Lectura(String habitacionId, Instant fechaHora, double temperaturaC) {
    }
}

