package com.ioteste.engine.repository;

import com.ioteste.engine.domain.Habitacion;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class HabitacionRepository {

    private final DataSource dataSource;

    public HabitacionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Habitacion> listar() {
        List<Habitacion> habitaciones = new ArrayList<>();
        String sql = "SELECT * FROM habitacion";
        try (Connection conexion = dataSource.getConnection(); 
             PreparedStatement statement = conexion.prepareStatement(sql);
             ResultSet resultado = statement.executeQuery()) {

             while (resultado.next()) {
                habitaciones.add(new Habitacion(
                        resultado.getInt("id"),
                        resultado.getString("nombre"),
                        resultado.getDouble("temperatura_objetivo"),
                        resultado.getString("termostato_id"),
                        resultado.getString("switch_id"),
                        resultado.getDouble("consumo")
                ));
             }
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar habitaciones", e);
        }
        return habitaciones;
    }

    public Habitacion buscarPorId(int id){
        String sql = "SELECT * FROM habitacion WHERE id = ?";
        try(Connection conexion = dataSource.getConnection();
            PreparedStatement statement = conexion.prepareStatement(sql)){

            statement.setInt(1, id);
            try (ResultSet resultado = statement.executeQuery()) {
                if(resultado.next()){
                    return new Habitacion(
                            resultado.getInt("id"),
                            resultado.getString("nombre"),
                            resultado.getDouble("temperatura_objetivo"),
                            resultado.getString("termostato_id"),
                            resultado.getString("switch_id"),
                            resultado.getDouble("consumo")
                    );
                }
            }
        } catch (SQLException e){
            throw new RuntimeException("Error al buscar habitacion", e);
        }
        return null;
    }

    public Habitacion crearHabitacion(Habitacion habitacion) {
        // TO - DO : Agregado consumo al INSERT
        String sql = """
                     INSERT INTO habitacion
                     (nombre, termostato_id, switch_id, temperatura_objetivo, consumo)
                     VALUES (?, ?, ?, ?, ?)
                     """;

        try (Connection conexion = dataSource.getConnection();
             PreparedStatement statement = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, habitacion.getNombre());
            statement.setString(2, habitacion.getIdTermostato());
            statement.setString(3, habitacion.getIdSwitch());
            statement.setDouble(4, habitacion.getTemperaturaEsperada());
            statement.setDouble(5, habitacion.getConsumo() != null ? habitacion.getConsumo() : 0.0);

            statement.executeUpdate();

            try(ResultSet resultado = statement.getGeneratedKeys()){
                if(resultado.next()){
                    return new Habitacion(
                            resultado.getInt(1),
                            habitacion.getNombre(),
                            habitacion.getTemperaturaEsperada(),
                            habitacion.getIdTermostato(),
                            habitacion.getIdSwitch(),
                            habitacion.getConsumo()
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al ingresar la habitacion", e);
        }
        return null;
    }

    public boolean eliminarHabitacion(int id){
        String sql = "DELETE FROM habitacion WHERE id = ?";
        try (Connection conexion = dataSource.getConnection();
             PreparedStatement statement = conexion.prepareStatement(sql)){

            statement.setInt(1,id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e){
            throw new RuntimeException("Error al eliminar habitacion", e);
        }
    }

    public int actualizarHabitacion(Habitacion habitacion){
        List<String> campos = new ArrayList<>();

        if (habitacion.getNombre() != null) campos.add("nombre = ?");
        if (habitacion.getTemperaturaEsperada() != null) campos.add("temperatura_objetivo = ?");
        if (habitacion.getIdTermostato() != null) campos.add("termostato_id = ?");
        if (habitacion.getIdSwitch() != null) campos.add("switch_id = ?");
        if (habitacion.getConsumo() != null) campos.add("consumo = ?"); // TODO (US4)

        if(campos.isEmpty()) return 400;

        String sql = "UPDATE habitacion SET " + String.join(", ", campos) + " WHERE id = ?";

        try (Connection conexion = dataSource.getConnection();
             PreparedStatement statement = conexion.prepareStatement(sql)){

            int parametro = 1;

            if (habitacion.getNombre() != null) statement.setString(parametro++, habitacion.getNombre());
            if (habitacion.getTemperaturaEsperada() != null) statement.setDouble(parametro++, habitacion.getTemperaturaEsperada());
            if (habitacion.getIdTermostato() != null) statement.setString(parametro++, habitacion.getIdTermostato());
            if (habitacion.getIdSwitch() != null) statement.setString(parametro++, habitacion.getIdSwitch());
            if (habitacion.getConsumo() != null) statement.setDouble(parametro++, habitacion.getConsumo()); // TODO (US4)
            
            statement.setInt(parametro, habitacion.getId());

            return statement.executeUpdate() == 0 ? 404 : 200;
        } catch(SQLException e){
            throw new RuntimeException("Error al actualizar habitacion", e);
        }
    }
}