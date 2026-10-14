package com.ioteste.engine.repository;

import com.ioteste.engine.domain.*;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class SitioRepository {
    private final DataSource dataSource;

    public SitioRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void guardarInventario(InventarioDTO inventario) throws SQLException {
        String deleteSitio = "DELETE FROM sitio"; // Borra en cascada las habitaciones viejas
        String insertSitio = "INSERT INTO sitio (id, nombre, potencia_contratada_kw, tarifa_desde, tarifa_hasta, tarifa_dias) VALUES (?, ?, ?, ?::time, ?::time, ?)";
        String insertHab = "INSERT INTO habitacion (id, sitio_id, nombre, temperatura_esperada, potencia_kw, id_termostato, topic_termostato, id_switch, url_switch) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false); // Inicio de transacción atómica
            try {
                try (PreparedStatement stmt = conn.prepareStatement(deleteSitio)) {
                    stmt.executeUpdate();
                }

                try (PreparedStatement stmt = conn.prepareStatement(insertSitio)) {
                    Sitio s = inventario.sitio();
                    stmt.setString(1, s.id());
                    stmt.setString(2, s.nombre());
                    stmt.setDouble(3, s.potenciaContratadaKW());
                    stmt.setString(4, s.tarifa().punta().desde());
                    stmt.setString(5, s.tarifa().punta().hasta());
                    stmt.setString(6, s.tarifa().punta().dias());
                    stmt.executeUpdate();
                }

                try (PreparedStatement stmt = conn.prepareStatement(insertHab)) {
                    for (Habitacion h : inventario.habitaciones()) {
                        stmt.setString(1, h.id());
                        stmt.setString(2, inventario.sitio().id());
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
                conn.commit(); // Si todo salió bien, guardamos definitivamente
            } catch (SQLException e) {
                conn.rollback(); // Si algo falló, revertimos
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
                    TarifaPunta punta = new TarifaPunta(rs.getString("tarifa_desde"), rs.getString("tarifa_hasta"), rs.getString("tarifa_dias"));
                    sitio = new Sitio(rs.getString("id"), rs.getString("nombre"), rs.getDouble("potencia_contratada_kw"), new Tarifa(punta));
                }
            }

            if (sitio == null) return null; // Retorna null si la tabla está vacía

            List<Habitacion> habitaciones = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(queryHab)) {
                stmt.setString(1, sitio.id());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        habitaciones.add(new Habitacion(
                            rs.getString("id"), rs.getString("nombre"), rs.getDouble("temperatura_esperada"), rs.getDouble("potencia_kw"),
                            rs.getString("id_termostato"), rs.getString("topic_termostato"), rs.getString("id_switch"), rs.getString("url_switch")
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