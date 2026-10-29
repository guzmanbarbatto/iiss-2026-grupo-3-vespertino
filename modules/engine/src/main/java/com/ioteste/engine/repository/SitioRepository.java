package com.ioteste.engine.repository;

import com.ioteste.engine.domain.Habitacion;
import com.ioteste.engine.domain.InventarioDTO;
import com.ioteste.engine.domain.Sitio;
import com.ioteste.engine.domain.Tarifa;
import com.ioteste.engine.domain.TarifaPunta;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class SitioRepository {

    private final DataSource dataSource;

    public SitioRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void guardarInventario(InventarioDTO inventario) throws SQLException {
        // Reemplazo por upsert en lugar de borrar todo: un DELETE FROM sitio borraba en cascada
        // las habitaciones y con ellas el histórico de temperaturas, dejando al motor sin lecturas.
        // Ahora solo se eliminan los sitios y habitaciones que ya no forman parte del inventario.
        String deleteOtrosSitios = "DELETE FROM sitio WHERE id <> ?";
        String upsertSitio = "INSERT INTO sitio (id, nombre, potencia_contratada_kw, tarifa_desde, tarifa_hasta, tarifa_dias) "
                           + "VALUES (?, ?, ?, ?::time, ?::time, ?) "
                           + "ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre, "
                           + "potencia_contratada_kw = EXCLUDED.potencia_contratada_kw, "
                           + "tarifa_desde = EXCLUDED.tarifa_desde, tarifa_hasta = EXCLUDED.tarifa_hasta, "
                           + "tarifa_dias = EXCLUDED.tarifa_dias";
        String deleteHabsRemovidas = "DELETE FROM habitacion WHERE sitio_id = ? AND NOT (id = ANY (?))";
        String upsertHab = "INSERT INTO habitacion (id, sitio_id, nombre, temperatura_esperada, potencia_kw, "
                         + "id_termostato, topic_termostato, id_switch, url_switch) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                         + "ON CONFLICT (id) DO UPDATE SET sitio_id = EXCLUDED.sitio_id, nombre = EXCLUDED.nombre, "
                         + "temperatura_esperada = EXCLUDED.temperatura_esperada, potencia_kw = EXCLUDED.potencia_kw, "
                         + "id_termostato = EXCLUDED.id_termostato, topic_termostato = EXCLUDED.topic_termostato, "
                         + "id_switch = EXCLUDED.id_switch, url_switch = EXCLUDED.url_switch";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false); // Inicio de transacción atómica
            try {
                Sitio s = inventario.sitio();
                List<Habitacion> habitacionesNuevas = inventario.habitaciones() == null
                        ? List.of() : inventario.habitaciones();

                // 1. Solo puede haber un sitio: se eliminan los demás (y en cascada sus habitaciones)
                try (PreparedStatement stmt = conn.prepareStatement(deleteOtrosSitios)) {
                    stmt.setString(1, s.id());
                    stmt.executeUpdate();
                }

                // 2. Se actualiza o inserta el sitio
                try (PreparedStatement stmt = conn.prepareStatement(upsertSitio)) {
                    stmt.setString(1, s.id());
                    stmt.setString(2, s.nombre());
                    stmt.setDouble(3, s.potenciaContratadaKW());
                    stmt.setString(4, s.tarifa().punta().desde());
                    stmt.setString(5, s.tarifa().punta().hasta());
                    stmt.setString(6, s.tarifa().punta().dias());
                    stmt.executeUpdate();
                }

                // 3. Se eliminan las habitaciones del sitio que ya no vienen en el inventario
                try (PreparedStatement stmt = conn.prepareStatement(deleteHabsRemovidas)) {
                    Object[] idsNuevos = habitacionesNuevas.stream().map(Habitacion::id).toArray();
                    stmt.setString(1, s.id());
                    stmt.setArray(2, conn.createArrayOf("varchar", idsNuevos));
                    stmt.executeUpdate();
                }

                // 4. Se insertan o actualizan las habitaciones vigentes sin tocar su histórico
                try (PreparedStatement stmt = conn.prepareStatement(upsertHab)) {
                    for (Habitacion h : habitacionesNuevas) {
                        stmt.setString(1, h.id());
                        stmt.setString(2, s.id());
                        stmt.setString(3, h.nombre());
                        stmt.setDouble(4, h.temperaturaEsperada());
                        stmt.setDouble(5, h.potenciaKW());
                        stmt.setString(6, h.idTermostato());
                        stmt.setString(7, h.topicTermostato());
                        stmt.setString(8, h.idSwitch());
                        stmt.setString(9, h.urlSwitch());
                        stmt.executeUpdate();
                    }
                }
                // Si todo salió bien, guardamos definitivamente
                conn.commit();
            } catch (SQLException e) {
                // Si algo falló, revertimos
                conn.rollback();
                throw e;
            }
        }
    }

    public InventarioDTO obtenerInventario() {
        String querySitio = "SELECT * FROM sitio LIMIT 1";
        String queryHab = "SELECT * FROM habitacion WHERE sitio_id = ?";

        try (Connection conn = dataSource.getConnection()) {
            Sitio sitio = null;
            try (PreparedStatement stmt = conn.prepareStatement(querySitio);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    TarifaPunta punta = new TarifaPunta(
                        rs.getString("tarifa_desde"),
                        rs.getString("tarifa_hasta"),
                        rs.getString("tarifa_dias")
                    );
                    sitio = new Sitio(
                        rs.getString("id"),
                        rs.getString("nombre"),
                        rs.getDouble("potencia_contratada_kw"),
                        new Tarifa(punta)
                    );
                }
            }

            // Retorna null si la tabla está vacía
            if (sitio == null) {
                return null;
            }

            List<Habitacion> habitaciones = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(queryHab)) {
                stmt.setString(1, sitio.id());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        habitaciones.add(new Habitacion(
                            rs.getString("id"),
                            rs.getString("nombre"),
                            rs.getDouble("temperatura_esperada"),
                            rs.getDouble("potencia_kw"),
                            rs.getString("id_termostato"),
                            rs.getString("topic_termostato"),
                            rs.getString("id_switch"),
                            rs.getString("url_switch")
                        ));
                    }
                }
            }
            return new InventarioDTO("2.0", sitio, habitaciones);
        } catch (SQLException e) {
            return null;
        }
    }
}
