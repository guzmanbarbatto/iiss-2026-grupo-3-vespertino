package com.ioteste.api.Repository;

import com.ioteste.api.Domain.Habitacion;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class HabitacionRepository {

private final String url = System.getenv("SPRING_DATASOURCE_URL") != null ? 
                               System.getenv("SPRING_DATASOURCE_URL") : "jdbc:postgresql://localhost:5432/ecowarm";
    private final String usuario = System.getenv("SPRING_DATASOURCE_USERNAME") != null ? 
                                   System.getenv("SPRING_DATASOURCE_USERNAME") : "ecowarm_user";
    private final String contrasenia = System.getenv("SPRING_DATASOURCE_PASSWORD") != null ? 
                                       System.getenv("SPRING_DATASOURCE_PASSWORD") : "ecowarmpass";

    public List<Habitacion> listar() {
        List<Habitacion> habitaciones = new ArrayList<>();

        String sql = "SELECT * FROM habitacion";

        try (Connection conexion = DriverManager.getConnection(url, usuario, contrasenia);

             PreparedStatement statement = conexion.prepareStatement(sql);
             ResultSet resultado = statement.executeQuery()) {

                 while (resultado.next()) {
                    Habitacion habitacion = new Habitacion(
                            resultado.getInt("id"),
                            resultado.getString("nombre"),
                            resultado.getDouble("temperatura_objetivo"),
                            resultado.getString("termostato_id"),
                            resultado.getString("switch_id")
                    );

                    habitaciones.add(habitacion);
                 }
            }
        catch (SQLException e) {
            throw new RuntimeException("Error al listar habitaciones", e);
        }

        return habitaciones;
    }

    public Habitacion buscarPorId(int id){
        String sql = "SELECT * FROM habitacion WHERE id = ?";

        try(Connection conexion = DriverManager.getConnection(url, usuario, contrasenia);
            PreparedStatement statement = conexion.prepareStatement(sql)){

            statement.setInt(1,id);

            try (ResultSet resultado = statement.executeQuery()) {

                if(resultado.next()){
                    return new Habitacion(
                            resultado.getInt("id"),
                            resultado.getString("nombre"),
                            resultado.getDouble("temperatura_objetivo"),
                            resultado.getString("termostato_id"),
                            resultado.getString("switch_id")
                    );
                }
            }
        }catch (SQLException e){
            throw new RuntimeException("Error al buscar habitacion", e);
        }
        return null;
    }

    public Habitacion crearHabitacion(Habitacion habitacion) {
        String sql = """
                     INSERT INTO habitacion
                     (nombre, termostato_id, switch_id, temperatura_objetivo)
                     VALUES (?, ?, ?, ?)
                     """;

        try (Connection conexion = DriverManager.getConnection(url, usuario, contrasenia);
             PreparedStatement statement = conexion.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, habitacion.getNombre());
            statement.setString(2, habitacion.getIdTermostato());
            statement.setString(3, habitacion.getIdSwitch());
            statement.setDouble(4, habitacion.getTemperaturaEsperada());

            statement.executeUpdate();

            try(ResultSet resultado = statement.getGeneratedKeys()){
                if(resultado.next()){
                    int id = resultado.getInt(1);

                    return new Habitacion(
                            id,
                            habitacion.getNombre(),
                            habitacion.getTemperaturaEsperada(),
                            habitacion.getIdTermostato(),
                            habitacion.getIdSwitch()
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

        try (Connection conexion = DriverManager.getConnection(url, usuario, contrasenia);
             PreparedStatement statement = conexion.prepareStatement(sql)){

            statement.setInt(1,id);
            int habitacionEliminada = statement.executeUpdate();

            return habitacionEliminada > 0;

        }catch (SQLException e){
            throw new RuntimeException("Error al eliminar habitacion", e);
        }
    }

    public int actualizarHabitacion(Habitacion habitacion){
        List<String> campos = new ArrayList<>();

        if (habitacion.getNombre() != null) {
            campos.add("nombre = ?");
        }
        if (habitacion.getTemperaturaEsperada() != null) {
            campos.add("temperatura_objetivo = ?");
        }
        if (habitacion.getIdTermostato() != null) {
            campos.add("termostato_id = ?");
        }
        if (habitacion.getIdSwitch() != null) {
            campos.add("switch_id = ?");
        }

        if(campos.isEmpty()){
            return 400;
        }

        String sql = "UPDATE habitacion SET " + String.join(", ", campos) + " WHERE id = ?";

        try (Connection conexion = DriverManager.getConnection(url, usuario, contrasenia);
             PreparedStatement statement = conexion.prepareStatement(sql)){

            int parametro = 1;

            if (habitacion.getNombre() != null) {
                statement.setString(parametro++, habitacion.getNombre());
            }if (habitacion.getTemperaturaEsperada() != null) {
                statement.setDouble(parametro++, habitacion.getTemperaturaEsperada());
            }if (habitacion.getIdTermostato() != null) {
                statement.setString(parametro++, habitacion.getIdTermostato());
            }if (habitacion.getIdSwitch() != null) {
                statement.setString(parametro++, habitacion.getIdSwitch());
            }
            statement.setInt(parametro, habitacion.getId());

            int habitacionActualizada = statement.executeUpdate();

            if(habitacionActualizada == 0){
                return 404;
            }
        }catch(SQLException e){
            throw new RuntimeException("Error al actualizar habitacion", e);
        }
        return 200;
    }
}