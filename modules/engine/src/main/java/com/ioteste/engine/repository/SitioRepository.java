package com.ioteste.engine.repository;

import com.ioteste.engine.domain.Franja;
import com.ioteste.engine.domain.Habitacion;
import com.ioteste.engine.domain.InventarioDTO;
import com.ioteste.engine.domain.Sitio;
import com.ioteste.engine.domain.Tarifa;
import java.sql.PreparedStatement;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persistencia del inventario del sitio (tablas {@code sitio} y {@code habitacion}). */
@Repository
public class SitioRepository {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private static final String UPSERT_SITIO = """
            INSERT INTO sitio (id, nombre, potencia_contratada_kw, tarifa_desde, tarifa_hasta, tarifa_dias,
                               schema_version)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre,
                potencia_contratada_kw = EXCLUDED.potencia_contratada_kw,
                tarifa_desde = EXCLUDED.tarifa_desde, tarifa_hasta = EXCLUDED.tarifa_hasta,
                tarifa_dias = EXCLUDED.tarifa_dias, schema_version = EXCLUDED.schema_version
            """;

    private static final String UPSERT_HABITACION = """
            INSERT INTO habitacion (id, sitio_id, orden, nombre, temperatura_esperada, potencia_kw,
                                    id_termostato, topic_termostato, id_switch, url_switch)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET sitio_id = EXCLUDED.sitio_id, orden = EXCLUDED.orden,
                nombre = EXCLUDED.nombre, temperatura_esperada = EXCLUDED.temperatura_esperada,
                potencia_kw = EXCLUDED.potencia_kw, id_termostato = EXCLUDED.id_termostato,
                topic_termostato = EXCLUDED.topic_termostato, id_switch = EXCLUDED.id_switch,
                url_switch = EXCLUDED.url_switch
            """;

    private final JdbcTemplate jdbc;

    public SitioRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Reemplaza el inventario en una sola transacción. Es un upsert, no un borrado total: borrar las
     * habitaciones arrastraría en cascada su histórico de temperaturas y dejaría al motor sin lecturas.
     * Solo se eliminan el sitio anterior (si cambió de id) y las habitaciones que ya no vienen.
     *
     * <p>En la misma transacción se anotan los switches de las habitaciones que salen, para que el motor los
     * apague aunque el engine se reinicie antes de lograrlo; y los que vuelven al inventario dejan de estar
     * pendientes, porque el motor los controla de nuevo.
     */
    @Transactional
    public void guardarInventario(InventarioDTO inventario) {
        Sitio sitio = inventario.sitio();
        Franja punta = sitio.tarifa().punta();
        List<Habitacion> habitaciones = inventario.habitaciones();

        String[] switchesVigentes = habitaciones.stream().map(Habitacion::idSwitch).toArray(String[]::new);
        conArray("""
                INSERT INTO switch_pendiente_apagado (id_switch)
                SELECT id_switch FROM habitacion WHERE NOT (id_switch = ANY (?))
                ON CONFLICT DO NOTHING
                """, switchesVigentes);
        conArray("DELETE FROM switch_pendiente_apagado WHERE id_switch = ANY (?)", switchesVigentes);

        jdbc.update("DELETE FROM sitio WHERE id <> ?", sitio.id());
        jdbc.update(UPSERT_SITIO, sitio.id(), sitio.nombre(), sitio.potenciaContratadaKW(),
                LocalTime.parse(punta.desde()), LocalTime.parse(punta.hasta()), punta.dias(),
                inventario.schemaVersion());

        String[] idsVigentes = habitaciones.stream().map(Habitacion::id).toArray(String[]::new);
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM habitacion WHERE sitio_id = ? AND NOT (id = ANY (?))");
            ps.setString(1, sitio.id());
            ps.setArray(2, con.createArrayOf("varchar", idsVigentes));
            return ps;
        });

        for (int i = 0; i < habitaciones.size(); i++) {
            Habitacion h = habitaciones.get(i);
            jdbc.update(UPSERT_HABITACION, h.id(), sitio.id(), i, h.nombre(), h.temperaturaEsperada(),
                    h.potenciaKW(), h.idTermostato(), h.topicTermostato(), h.idSwitch(), h.urlSwitch());
        }
    }

    /** Switches de habitaciones retiradas del inventario que todavía hay que apagar. */
    public List<String> switchesPendientesDeApagar() {
        return jdbc.queryForList("SELECT id_switch FROM switch_pendiente_apagado ORDER BY id_switch", String.class);
    }

    /** El switch ya se apagó (o no existe): deja de estar pendiente. */
    public void quitarPendienteDeApagar(String idSwitch) {
        jdbc.update("DELETE FROM switch_pendiente_apagado WHERE id_switch = ?", idSwitch);
    }

    private void conArray(String sql, String[] valores) {
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setArray(1, con.createArrayOf("varchar", valores));
            return ps;
        });
    }

    /** El inventario persistido, con las habitaciones en el orden en que se cargaron. */
    public Optional<InventarioDTO> obtenerInventario() {
        List<InventarioDTO> sitios = jdbc.query("""
                SELECT id, nombre, potencia_contratada_kw, tarifa_desde, tarifa_hasta, tarifa_dias, schema_version
                FROM sitio LIMIT 1
                """, (rs, n) -> new InventarioDTO(
                        rs.getString("schema_version"),
                        new Sitio(
                                rs.getString("id"),
                                rs.getString("nombre"),
                                rs.getDouble("potencia_contratada_kw"),
                                new Tarifa(new Franja(
                                        rs.getObject("tarifa_desde", LocalTime.class).format(HORA),
                                        rs.getObject("tarifa_hasta", LocalTime.class).format(HORA),
                                        rs.getString("tarifa_dias")))),
                        null));
        if (sitios.isEmpty()) {
            return Optional.empty();
        }

        InventarioDTO sinHabitaciones = sitios.get(0);
        List<Habitacion> habitaciones = jdbc.query("""
                SELECT id, nombre, temperatura_esperada, potencia_kw, id_termostato, topic_termostato,
                       id_switch, url_switch
                FROM habitacion WHERE sitio_id = ? ORDER BY orden, id
                """, (rs, n) -> new Habitacion(
                        rs.getString("id"),
                        rs.getString("nombre"),
                        rs.getDouble("temperatura_esperada"),
                        rs.getDouble("potencia_kw"),
                        rs.getString("id_termostato"),
                        rs.getString("topic_termostato"),
                        rs.getString("id_switch"),
                        rs.getString("url_switch")),
                sinHabitaciones.sitio().id());

        return Optional.of(new InventarioDTO(sinHabitaciones.schemaVersion(), sinHabitaciones.sitio(), habitaciones));
    }
}
