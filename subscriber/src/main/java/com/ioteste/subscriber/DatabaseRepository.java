package com.ioteste.subscriber;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseRepository {
    private static final Logger LOGGER = Logger.getLogger(DatabaseRepository.class.getName());
    private final String dbUrl;
    private final String dbUser;
    private final String dbPass;

    public DatabaseRepository(String dbUrl, String dbUser, String dbPass) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPass = dbPass;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPass);
    }

    public String getHabitacionNombre(int id) {
        String sql = "SELECT nombre FROM habitacion WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("nombre");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al consultar el nombre de la habitación", e);
        }
        return "Desconocida";
    }

    public void insertarHistorico(int idHabitacion, long timestampMillis, double temperatura) {
        String sql = "INSERT INTO historico_temperatura (habitacion_id, fecha_hora, temperatura_c) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, idHabitacion);
            pstmt.setTimestamp(2, new Timestamp(timestampMillis));
            pstmt.setDouble(3, temperatura);
            pstmt.executeUpdate();
            
            String nombre = getHabitacionNombre(idHabitacion);
            LOGGER.info(String.format("Transacción exitosa: %s°C guardado para la habitación '%s' (ID: %d).", temperatura, nombre, idHabitacion));
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al insertar el histórico", e);
        }
    }
}